(ns converter.traktor.core
  (:require
   [cemerick.url :as url]
   [clojure.data.zip.xml :as zx]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.string :as str]
   [clojure.zip :as zip]
   [converter.map :as map]
   [converter.spec :as spec]
   [converter.traktor.album :as ta]
   [converter.traktor.cue :as tc]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]))

(def location
  {:tag (s/spec #{:LOCATION})
   :attrs {:DIR ::spec/not-blank ; TODO use ::spec/nml-dir
           :FILE ::spec/not-blank
           (std/opt :VOLUME) (std/or {:drive-letter ::spec/drive-letter ; how can I say, if no volume also no volumeid?
                                      :not-drive-letter ::spec/not-blank})
           (std/opt :VOLUMEID) ::spec/not-blank}})

(def location-spec
  (std/spec {:name ::location
             :spec location}))

(s/fdef url->location
  :args (s/cat :location ::spec/url)
  :ret location-spec)

(defn url->location
  [{:keys [:path]}]
  (let [path (str/split path #"/")]
    {:tag :LOCATION
     :attrs {:DIR (str (str/join "/:" (drop-last path)) "/:")
             :FILE (last path)}}))

(s/fdef location->url
  :args (s/cat :location-z (spec/xml-zip-spec location-spec))
  :ret ::spec/url)

(defn location->url
  [location-z]
  (let [dir (zx/attr location-z :DIR)
        file (zx/attr location-z :FILE)
        volume (zx/attr location-z :VOLUME)]
    (apply url/url (as-> [] $
                     (conj $ "file://localhost")
                     (conj $ (if (spec/drive-letter? volume) (str "/" volume) ""))
                     (reduce conj $ (map url/url-encode (str/split dir #"/:")))
                     (conj $ (url/url-encode file))))))

(def entry
  {:tag (s/spec #{:ENTRY})
   :attrs {(std/opt :TITLE) string?
           (std/opt :ARTIST) string?}
   :content      (s/cat
                  :location location-spec
                  :album (s/? (std/spec {:name ::album
                                             :spec {:tag (s/spec #{:ALBUM})
                                                    :attrs (s/keys :req-un [(or ::ta/TRACK ::ta/TITLE)])}}))
                  :modification-info (s/? (std/spec {:name ::modification-info
                                                     :spec {:tag (s/spec #{:MODIFICATION_INFO})}}))
                  :info (s/? (std/spec {:name ::info
                                        :spec {:tag (s/spec #{:INFO})
                                               :attrs {(std/opt :PLAYTIME) string?}}}))
                  :tempo (s/? (std/spec {:name ::tempo
                                         :spec {:tag (s/spec #{:TEMPO})
                                                :attrs {(std/opt :BPM) string?}}}))
                  :loudness (s/? (std/spec {:name ::loudness
                                            :spec {:tag (s/spec #{:LOUDNESS})}}))
                  :musical-key (s/? (std/spec {:name ::musical-key
                                               :spec {:tag (s/spec #{:MUSICAL_KEY})}}))
                  :loopinfo (s/? (std/spec {:name ::loopinfo
                                            :spec {:tag (s/spec #{:LOOPINFO})}}))
                  :cue (s/* tc/cue-spec))})

(def entry-spec
  (std/spec
   {:name ::entry
    :spec entry}))

(defn equiv-bpm?
  [{:keys [::u/tempos] :as item} entry-z]
  (let [tempo-z (zx/xml1-> entry-z :TEMPO)
        bpm (and tempo-z (zx/attr tempo-z :BPM))]
    (if (empty? tempos)
      (= (::u/bpm item) bpm)
      (= (::ut/bpm (first tempos)) bpm))))

(s/fdef item->entry
  :args (s/cat :item u/item-spec)
  :fn (fn equiv-entry? [{{conformed-item :item} :args conformed-entry :ret}]
        (let [item (s/unform u/item-spec conformed-item)
              entry-z (zip/xml-zip (s/unform entry-spec conformed-entry))]
          (and
           (= (::u/title item) (zx/attr entry-z :TITLE))
           (equiv-bpm? item entry-z))))
  :ret entry-spec)

(defn item->entry
  [{:keys [::u/location ::u/title ::u/artist ::u/track ::u/album ::u/time ::u/bpm ::u/tempos ::u/markers]}]
  {:tag :ENTRY
   :attrs (cond-> {}
            title (assoc :TITLE title)
            artist (assoc :ARTIST artist))
   :content (cond-> []
              true (conj (url->location location))
              (or track album) (conj {:tag :ALBUM
                                      :attrs (cond-> {}
                                               track (assoc :TRACK track)
                                               album (assoc :TITLE album))})
              time (conj {:tag :INFO
                          :attrs {:PLAYTIME time}})
              bpm (conj {:tag :TEMPO
                         :attrs {:BPM (if (empty? tempos) bpm (::ut/bpm (first tempos)))}}) ; if there are tempos take the first tempo as bpm (since item bpm could be an average), otherwise take item bpm
              markers (concat (map tc/marker->cue markers)))})

(defn grid-markers->tempos
  [{:keys [::u/bpm ::u/markers] :as item}]
  (as-> item $
    (reduce #(update %1 ::u/tempos
                     (fn [tempos marker] (if (and
                                              bpm
                                              (= ::um/type-grid (::um/type marker)))
                                           (conj tempos {::ut/inizio (::um/start marker)
                                                         ::ut/bpm bpm ; only one tempo/bpm value for the whole track, in traktor
                                                         ::ut/metro "4/4"
                                                         ::ut/battito "1"})
                                           tempos)) %2)
            $
            markers)
    (map/remove-nil $ ::u/tempos)))

(defn equiv-tempo?
  [entry-z item]
  (let [tempo-z (zx/xml1-> entry-z :TEMPO)
        bpm (and tempo-z (zx/attr tempo-z :BPM))
        grid-cues-z (zx/xml-> entry-z :CUE_V2 (zx/attr= :TYPE "4"))]
    (for [grid-cue-z grid-cues-z
          tempo (::u/tempos item)]
      (and (= (zx/attr grid-cue-z :START) (::ut/inizio tempo))
           (= bpm (::ut/bpm tempo))))))

(defn equiv-markers?
  [entry-z item]
  (let [cues-z (zx/xml-> entry-z :CUE_V2)]
    (for [cue-z cues-z
          marker (::u/markers item)]
      (= (zx/attr cue-z :START) (::um/start marker)))))

(s/fdef entry->item
  :args (s/cat :entry (spec/xml-zip-spec entry-spec))
  :fn (fn equiv-item? [{{conformed-entry :entry} :args conformed-item :ret}]
        (let [entry-z (zip/xml-zip (s/unform entry-spec conformed-entry))
              item (s/unform u/item-spec conformed-item)]
          (and
           (= (zx/attr entry-z :TITLE) (::u/title item))
           (= (zx/attr entry-z :ARTIST) (::u/artist item))
           (equiv-tempo? entry-z item)
           
           )))
  :ret u/item-spec)

(defn entry->item
  [entry-z]
  (let [title (zx/attr entry-z :TITLE)
        artist (zx/attr entry-z :ARTIST)
        album-z (zx/xml1-> entry-z :ALBUM)
        track (and album-z (zx/attr album-z :TRACK))
        album-title (and album-z (zx/attr album-z :TITLE))
        info-z (zx/xml1-> entry-z :INFO)
        playtime (and info-z (zx/attr info-z :PLAYTIME))
        tempo-z (zx/xml1-> entry-z :TEMPO)
        bpm (and tempo-z (zx/attr tempo-z :BPM))
        cues-z (zx/xml-> entry-z :CUE_V2)]
    (->
     (cond-> {::u/location (location->url (zx/xml1-> entry-z :LOCATION))}
       title (assoc ::u/title title)
       artist (assoc ::u/artist artist)
       track (assoc ::u/track track)
       album-title (assoc ::u/album album-title)
       playtime (assoc ::u/time playtime)
       bpm (assoc ::u/bpm bpm)
       (not-empty cues-z) (assoc ::u/markers (map tc/cue->marker cues-z)))
     u/sorted-markers
     grid-markers->tempos
     u/sorted-tempos)))

(defn library->nml
  [_ library]
  {:tag :NML
   :attrs {:VERSION 19}
   :content [{:tag :COLLECTION :content (map item->entry (::u/collection library))}]})

(defn nml->library
  [_ nml]
  (if (xml/xml? nml)
    (let [nml-z (zip/xml-zip nml)
          collection-z (zx/xml1-> nml-z :COLLECTION)]
      {::u/collection (map entry->item (zx/xml-> collection-z :ENTRY))})
    nml))

(def nml
  {:tag (s/spec #{:NML})
   :attrs {:VERSION (st/spec #{19} {:type :long})}
   :content (s/cat
             :head (s/? (std/spec {:name ::head
                                   :spec {:tag (s/spec #{:HEAD})}}))
             :musicfolders (s/? (std/spec {:name ::musicfolders
                                           :spec {:tag (s/spec #{:MUSICFOLDERS})}}))
             :collection (std/spec
                          {:name ::collection
                           :spec {:tag (s/spec #{:COLLECTION})
                                  :content (s/cat :entries (s/* entry-spec))}})
             :sets (s/? (std/spec {:name ::sets
                                   :spec {:tag (s/spec #{:SETS})}}))
             :playlists (s/? (std/spec {:name ::playlists
                                        :spec {:tag (s/spec #{:PLAYLISTS})}}))
             :sorting-order (s/* (std/spec {:name ::sorting-order
                                            :spec {:tag (s/spec #{:SORTING_ORDER})}})))})

(def nml-spec
  (->
   (std/spec
    {:name ::nml
     :spec nml})
   (assoc
    :encode/xml library->nml)))

(def library-spec
  (-> u/library-spec
      (assoc :decode/xml nml->library)))
