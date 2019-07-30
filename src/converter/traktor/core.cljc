(ns converter.traktor.core
  (:require
   [cemerick.url :refer [url url-encode url-decode]]
   [clojure.data.zip.xml :as zx]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :refer [split join]]
   [clojure.zip :as zip]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.traktor.album :as ta]
   [converter.traktor.cue :as tc]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.url :as url]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]
   [utils.map :as map]))

(def nml-path-sep
  "/:")

(def nml-path-sep-regex
  #"/:")

(defn nml-dir-gen
  []
  (gen/fmap #(->> % (interleave (repeat nml-path-sep)) (apply str)) (gen/vector (str/not-blank-string-with-whitespace-gen))))

(s/def ::nml-dir
  (s/with-gen
    string? ; TODO and with cat+regex specs
    (fn [] (nml-dir-gen))))

(s/def ::nml-path
  (s/with-gen
    string? ; TODO and with cat+regex specs
    (fn [] (gen/fmap (partial apply str)
                     (gen/tuple
                      ; drive letter (optional)
                      (gen/one-of [(str/drive-letter-gen) (gen/elements #{""})])
                      ; dir
                      (nml-dir-gen)
                      ; filename
                      (gen/fmap #(str nml-path-sep %) (str/not-blank-string-with-whitespace-gen)))))))

(def location
  {:tag (s/spec #{:LOCATION})
   :attrs {:DIR ::nml-dir
           :FILE ::str/not-blank-string
           (std/opt :VOLUME) (std/or {:drive-letter ::str/drive-letter
                                      :not-drive-letter ::str/not-blank-string})
           (std/opt :VOLUMEID) ::str/not-blank-string}})

(def location-spec
  (spec/such-that-spec
   (std/spec {:name ::location
              :spec location})
   #(or (and (-> % :attrs :VOLUME) (-> % :attrs :VOLUMEID))
        (and (not (-> % :attrs :VOLUME)) (not (-> % :attrs :VOLUMEID))))
   10))

(s/fdef url->location
  :args (s/cat :location ::url/url)
  :ret location-spec)

(defn url->location
  [{:keys [:path]}]
  (let [paths (rest (split path #"/"))
        dirs (if (str/drive-letter? (first paths)) (rest (drop-last paths)) (drop-last paths))
        file (last paths)
        volume (if (str/drive-letter? (first paths)) (first paths))]
    {:tag :LOCATION
     :attrs (cond-> {:DIR (str nml-path-sep (join nml-path-sep (map url-decode dirs)))
                     :FILE (url-decode file)}
              volume (assoc :VOLUME volume))}))

(s/fdef location->url
  :args (s/cat :location-z (spec/xml-zip-spec location-spec))
  :ret ::url/url)

(defn location->url
  [location-z]
  (let [dir (zx/attr location-z :DIR)
        file (zx/attr location-z :FILE)
        volume (zx/attr location-z :VOLUME)]
    (apply url (as-> [] $
                 (conj $ "file://localhost")
                 (conj $ (if (str/drive-letter? volume) (str "/" volume) ""))
                 (reduce conj $ (map url-encode (split dir nml-path-sep-regex)))
                 (conj $ (url-encode file))))))

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
                                               :attrs {(std/opt :COMMENT) string?
                                                       (std/opt :GENRE) string?
                                                       (std/opt :PLAYTIME) string?}}}))
                  :tempo (s/? (std/spec {:name ::tempo
                                         :spec {:tag (s/spec #{:TEMPO})
                                                :attrs {(std/opt :BPM) (s/double-in :min 0 :NaN? false :infinite? false)}}}))
                  :loudness (s/? (std/spec {:name ::loudness
                                            :spec {:tag (s/spec #{:LOUDNESS})}}))
                  :musical-key (s/? (std/spec {:name ::musical-key
                                               :spec {:tag (s/spec #{:MUSICAL_KEY})}}))
                  :loopinfo (s/? (std/spec {:name ::loopinfo
                                            :spec {:tag (s/spec #{:LOOPINFO})}}))
                  :cue (s/* tc/cue-visible-or-hidden-spec))})

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

; TODO equiv-cues
(s/fdef item->entry
  :args (s/cat :item u/item-spec)
  :fn (fn equiv-entry? [{{conformed-item :item} :args conformed-entry :ret}]
        (let [item (s/unform u/item-spec conformed-item)
              entry-z (zip/xml-zip (s/unform entry-spec conformed-entry))
              info-z (zx/xml1-> entry-z :INFO)]
          (and
           (= (::u/title item) (zx/attr entry-z :TITLE))
           (= (::u/artist item) (zx/attr entry-z :ARTIST))
           (= (::u/total-time item) (and info-z (zx/attr info-z :PLAYTIME)))
           (= (::u/comments item) (and info-z (zx/attr info-z :COMMENT)))
           (= (::u/genre item) (and info-z (zx/attr info-z :GENRE)))
           (equiv-bpm? item entry-z))))
  :ret entry-spec)

(defn hidden-grid-cues-for-tempos-without-matching-markers
  [tempos markers]
  (let [tempos-without-matching-markers (filter #(empty? (filter (fn [marker] (= (::ut/inizio %) (::um/start marker))) markers)) tempos)]
    ; TODO report warning if tempo bpm differs from item bpm
    (map #(tc/hidden-grid-cue (::ut/inizio %1)) tempos-without-matching-markers)))

(defn item->entry
  [{:keys [::u/location ::u/title ::u/artist ::u/track-number ::u/album  ::u/total-time ::u/bpm ::u/comments ::u/genre ::u/tempos ::u/markers]}]
  {:tag :ENTRY
   ; TODO need to assoc MODIFIED_DATE and MODIFIED_TIME, and these must be 'newer' to replace existing data in Traktor
   ; but Rekordbox xml doesn't have this data..
   ; naive solution for now - just use "run datetime = now" for all items
   ; slightly less naive solution - calc hash of items on both sides, then filter using hash1 != hash2, and then set "run datetime = now"
   :attrs (cond-> {}
            title (assoc :TITLE title)
            artist (assoc :ARTIST artist))
   :content (cond-> []
              true (conj (url->location location))
              (or track-number album) (conj {:tag :ALBUM
                                             :attrs (cond-> {}
                                                      track-number (assoc :TRACK track-number)
                                                      album (assoc :TITLE album))})
              (or comments genre total-time) (conj {:tag :INFO
                                                    :attrs (cond-> {}
                                                             comments (assoc :COMMENT comments)
                                                             genre (assoc :GENRE genre)
                                                             total-time (assoc :PLAYTIME total-time))})
              bpm (conj {:tag :TEMPO
                         :attrs {:BPM (if (empty? tempos) bpm (::ut/bpm (first tempos)))}}) ; if there are tempos take the first tempo as bpm (since item bpm could be an average), otherwise take item bpm
              markers (concat (map tc/marker->cue markers) (hidden-grid-cues-for-tempos-without-matching-markers tempos markers)))})

; TODO move to cue ns?
(defn grid-cue->tempo
  [bpm cue-z]
  {::ut/inizio (tc/millis->seconds (zx/attr cue-z :START))
   ::ut/bpm bpm
   ::ut/metro "4/4"
   ::ut/battito "1"})

(defn equiv-tempo?
  [entry-z item]
  (let [tempo-z (zx/xml1-> entry-z :TEMPO)
        bpm (and tempo-z (zx/attr tempo-z :BPM))
        grid-cues-z (zx/xml-> entry-z :CUE_V2 (zx/attr= :TYPE "4"))]
    (every? identity
            (map #(and
                   (= (tc/millis->seconds (zx/attr %1 :START)) (::ut/inizio %2))
                   (= bpm (::ut/bpm %2)))
                 grid-cues-z
                 (::u/tempos item)))))

(defn equiv-markers?
  [entry-z item]
  (let [visible-cues-z (remove (comp tc/hidden-cue? zip/node) (zx/xml-> entry-z :CUE_V2))]
    (every? identity
            (map #(= (tc/millis->seconds (zx/attr %1 :START)) (::um/start %2))
                 visible-cues-z
                 (::u/markers item)))))

(s/fdef entry->item
  :args (s/cat :entry (spec/xml-zip-spec entry-spec))
  :fn (fn equiv-item? [{{conformed-entry :entry} :args conformed-item :ret}]
        (let [entry-z (zip/xml-zip (s/unform entry-spec conformed-entry))
              info-z (zx/xml1-> entry-z :INFO)
              item (s/unform u/item-spec conformed-item)]
          (and
           (= (zx/attr entry-z :TITLE) (::u/title item))
           (= (zx/attr entry-z :ARTIST) (::u/artist item))
           (= (and info-z (zx/attr info-z :COMMENT)) (::u/comments item))
           (= (and info-z (zx/attr info-z :GENRE)) (::u/genre item))
           (= (and info-z (zx/attr info-z :PLAYTIME)) (::u/total-time item))
           (equiv-markers? entry-z item)
           (equiv-tempo? entry-z item))))
  :ret u/item-spec)

(defn entry->item
  [entry-z]
  (let [title (zx/attr entry-z :TITLE)
        artist (zx/attr entry-z :ARTIST)
        album-z (zx/xml1-> entry-z :ALBUM)
        track (and album-z (zx/attr album-z :TRACK))
        album-title (and album-z (zx/attr album-z :TITLE))
        info-z (zx/xml1-> entry-z :INFO)
        comment (and info-z (zx/attr info-z :COMMENT))
        genre (and info-z (zx/attr info-z :GENRE))
        playtime (and info-z (zx/attr info-z :PLAYTIME))
        tempo-z (zx/xml1-> entry-z :TEMPO)
        bpm (and tempo-z (zx/attr tempo-z :BPM))
        visible-cues-z (remove (comp tc/hidden-cue? zip/node) (zx/xml-> entry-z :CUE_V2))
        grid-cues-z (zx/xml-> entry-z :CUE_V2 (zx/attr= :TYPE "4"))]
    (cond-> {::u/location (location->url (zx/xml1-> entry-z :LOCATION))}
      title (assoc ::u/title title)
      artist (assoc ::u/artist artist)
      track (assoc ::u/track-number track)
      album-title (assoc ::u/album album-title)
      comment (assoc ::u/comments comment)
      genre (assoc ::u/genre genre)
      playtime (assoc ::u/total-time playtime)
      bpm (assoc ::u/bpm bpm)
      (not-empty visible-cues-z) (assoc ::u/markers (map tc/cue->marker visible-cues-z))
      (and bpm (not-empty grid-cues-z)) (assoc ::u/tempos (map (partial grid-cue->tempo bpm) grid-cues-z)))))

(defn library->nml
  [progress _ {:keys [::u/collection]}]
  {:tag :NML
   :attrs {:VERSION 19}
   :content [{:tag :COLLECTION
              :content (map (if progress (progress item->entry) item->entry) collection)}]})

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

(defn nml-spec
  ([]
   (nml-spec nil))
  ([progress]
   (->
    (std/spec
     {:name ::nml
      :spec nml})
    (assoc :encode/xml (partial library->nml progress)))))

(s/fdef library->nml
  :args (s/cat :progress nil? :library-spec any? :library u/library-spec)
  :ret (nml-spec)
  :fn (fn equiv-collection-counts? [{{conformed-library :library} :args conformed-nml :ret}]
        (let [library (s/unform u/library-spec conformed-library)
              nml-z (zip/xml-zip (s/unform (nml-spec) conformed-nml))
              collection-z (zx/xml1-> nml-z :COLLECTION)]
          (= (count (->> library ::u/collection))
             (count (zx/xml-> collection-z :ENTRY))))))

(def library-spec
  (-> u/library-spec
      (assoc :decode/xml nml->library)))
