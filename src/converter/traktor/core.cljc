(ns converter.traktor.core
  (:require
   [cemerick.url :refer [url url-encode url-decode]]
   #?(:clj [clojure.core.async :as async] :cljs [cljs.core.async :as async])
   [clojure.data.zip.xml :as zx]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as string]
   [clojure.zip :as zip]
   [converter.config :as config]
   [converter.time :as time]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.time :as time]
   [converter.traktor.album :as ta]
   [converter.traktor.cue :as tc]
   [converter.traktor.location :as tl]
   [converter.traktor.nml :as nml]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.url :as url]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]
   [tick.alpha.api :as tick]
   [utils.map :as map]
   #?(:clj [taoensso.tufte :as tufte :refer (defnp p profile)]
      :cljs [taoensso.tufte :as tufte :refer-macros (defnp p profile)])))

(def xml-transformer
  (spec/xml-transformer nml/nml-date-format))

(def string-transformer
  (spec/string-transformer nml/nml-date-format))

(def entry
  {:tag (s/spec #{:ENTRY})
   :attrs {(std/opt :MODIFIED_DATE) (time/date-str-spec nml/nml-date-format)
           (std/opt :MODIFIED_TIME) ::time/seconds-per-day
           (std/opt :TITLE) string?
           (std/opt :ARTIST) string?}
   :content      (s/cat
                  :location (s/? tl/location-spec)
                  :album (s/? (std/spec {:name ::album
                                         :spec {:tag (s/spec #{:ALBUM})
                                                :attrs (s/keys :req-un [(or ::ta/TRACK ::ta/TITLE)])}}))
                  :modification-info (s/? (std/spec {:name ::modification-info
                                                     :spec {:tag (s/spec #{:MODIFICATION_INFO})}}))
                  :info (s/? (std/spec {:name ::info
                                        :spec {:tag (s/spec #{:INFO})
                                               :attrs {(std/opt :COMMENT) string?
                                                       (std/opt :GENRE) string?
                                                       ; FIXME encoding to ::time/date won't work until this issue is fixed: 
                                                       ; https://github.com/metosin/spec-tools/issues/183
                                                       (std/opt :IMPORT_DATE) (time/date-str-spec nml/nml-date-format)
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
                  :cue (s/* tc/cue-spec)
                  :stems (s/* (std/spec {:name ::stems
                                         :spec {:tag (s/spec #{:STEMS})}})))})

(def entry-spec
  (std/spec
   {:name ::entry
    :spec entry}))

(defn equiv-bpm?
  [item entry-z]
  (let [tempo-z (zx/xml1-> entry-z :TEMPO)
        bpm (and tempo-z (zx/attr tempo-z :BPM))]
    (= (::u/bpm item) bpm)))

; TODO equiv-cues, which needs to cover tc/marker->cue and tc/tempo->cue-tagged
(s/fdef item->entry
  :args (s/cat :nml-date (time/date-str-spec nml/nml-date-format)
               :nml-time ::time/seconds-per-day
               :item u/item-spec)
  :ret entry-spec
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
           (= (::u/date-added item) (nml/string->date (and info-z (zx/attr info-z :IMPORT_DATE))))
           (equiv-bpm? item entry-z)))))

(defn item->entry
  [nml-date nml-time {:keys [::u/location ::u/title ::u/artist ::u/track-number ::u/album
                             ::u/total-time ::u/bpm ::u/date-added ::u/comments ::u/genre
                             ::u/tempos ::u/markers]}]
  (p ::item->entry
     {:tag :ENTRY
      :attrs (cond-> {}
               true (assoc :MODIFIED_DATE nml-date
                           :MODIFIED_TIME nml-time)
               title (assoc :TITLE title)
               artist (assoc :ARTIST artist))
      :content (cond-> []
                 true (conj (tl/url->location location))
                 (or track-number album) (conj {:tag :ALBUM
                                                :attrs (cond-> {}
                                                         track-number (assoc :TRACK track-number)
                                                         album (assoc :TITLE album))})
                 (or date-added comments genre total-time) (conj {:tag :INFO
                                                                  :attrs (cond-> {}
                                                                           date-added (assoc :IMPORT_DATE (nml/date->string date-added))
                                                                           comments (assoc :COMMENT comments)
                                                                           genre (assoc :GENRE genre)
                                                                           total-time (assoc :PLAYTIME total-time))})
                 bpm (conj {:tag :TEMPO
                            :attrs {:BPM bpm}})
                 markers (concat (map tc/marker->cue 
                                      (concat (um/indexed-markers markers) (um/non-indexed-markers-without-matching-indexed-marker markers))))
                 tempos (concat (map tc/tempo->cue-tagged 
                                     (u/tempos-without-matching-markers tempos markers))))}))

(defn equiv-tempos?
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
  [entry-z {:keys [::u/markers]}]
  (let [cues-z (remove (comp tc/cue-tagged? zip/node) (zx/xml-> entry-z :CUE_V2))]
    (and
     (= (count cues-z) (count markers))
     (every? identity
             (map #(= (tc/millis->seconds (zx/attr %1 :START)) (::um/start %2))
                  cues-z
                  markers)))))

; returns the entry location, or nil if it doesn't have a location
(defn location-z
  [entry-z]
  (zx/xml1-> entry-z :LOCATION))

; TODO would rather use data.zip.xml api all the way down, 
; but spec/such-that-spec can't currently be wrapped around spec/xml-zip-spec
(defn entry-has-location?
  [entry]
  (location-z (zip/xml-zip entry)))

(s/fdef entry->item
  :args (s/cat :entry (spec/xml-zip-spec (spec/such-that-spec entry-spec entry-has-location? 100)))
  :fn (fn equiv-item? [{{conformed-entry :entry} :args conformed-item :ret}]
        (let [entry-z (zip/xml-zip (s/unform entry-spec conformed-entry))
              info-z (zx/xml1-> entry-z :INFO)
              item (s/unform u/item-spec conformed-item)]
          (and
           (= (zx/attr entry-z :TITLE) (::u/title item))
           (= (zx/attr entry-z :ARTIST) (::u/artist item))
           (= (and info-z (zx/attr info-z :COMMENT)) (::u/comments item))
           (= (and info-z (zx/attr info-z :GENRE)) (::u/genre item))
           (= (and info-z (zx/attr info-z :IMPORT_DATE)) (nml/date->string (::u/date-added item)))
           (= (and info-z (zx/attr info-z :PLAYTIME)) (::u/total-time item))
           (equiv-markers? entry-z item)
           (equiv-tempos? entry-z item))))
  :ret u/item-spec)

(defn entry->item
  [entry-z]
  (p ::entry->item
     (let [title (zx/attr entry-z :TITLE)
           artist (zx/attr entry-z :ARTIST)
           album-z (zx/xml1-> entry-z :ALBUM)
           track (and album-z (zx/attr album-z :TRACK))
           album-title (and album-z (zx/attr album-z :TITLE))
           info-z (zx/xml1-> entry-z :INFO)
           import-date (and info-z (zx/attr info-z :IMPORT_DATE))
           comment (and info-z (zx/attr info-z :COMMENT))
           genre (and info-z (zx/attr info-z :GENRE))
           playtime (and info-z (zx/attr info-z :PLAYTIME))
           tempo-z (zx/xml1-> entry-z :TEMPO)
           bpm (and tempo-z (zx/attr tempo-z :BPM))
           cues-z (remove (comp tc/cue-tagged? zip/node) (zx/xml-> entry-z :CUE_V2))
           grid-cues-z (zx/xml-> entry-z :CUE_V2 (zx/attr= :TYPE "4"))]
       (cond-> {::u/location (tl/location->url (zx/xml1-> entry-z :LOCATION))}
         title (assoc ::u/title title)
         artist (assoc ::u/artist artist)
         track (assoc ::u/track-number track)
         album-title (assoc ::u/album album-title)
         import-date (assoc ::u/date-added (nml/string->date import-date))
         comment (assoc ::u/comments comment)
         genre (assoc ::u/genre genre)
         playtime (assoc ::u/total-time playtime)
         bpm (assoc ::u/bpm bpm)
         (not-empty cues-z) (assoc ::u/markers (map tc/cue->marker cues-z))
         (and bpm (not-empty grid-cues-z)) (assoc ::u/tempos (map (partial tc/cue->tempo bpm) grid-cues-z))))))

(defn library->nml
  [{:keys [progress clock]} _ {:keys [::u/collection]}]
  {:tag :NML
   :attrs {:VERSION 19}
   :content [{:tag :COLLECTION
              :content (let [instant (tick/with-clock clock (tick/instant))
                             nml-date (nml/date->string (tick/date instant))
                             nml-time (tick/seconds (tick/between (tick/truncate instant :days) instant))]
                         (map (progress (partial item->entry nml-date nml-time))
                              collection))}]})

(defn nth-entry
  [nml index]
  (-> nml
      zip/xml-zip
      (zx/xml1-> :COLLECTION)
      (zx/xml-> :ENTRY)
      (nth index)
      zip/node))

(defn entries-z
  [collection-z]
  (filter #(and (location-z %)
                (tl/location-z-file-is-not-blank? (location-z %)))
          (zx/xml-> collection-z :ENTRY)))

(defn nml->library
  [_ nml]
  (if (xml/xml? nml)
    (let [nml-z (zip/xml-zip nml)
          collection-z (zx/xml1-> nml-z :COLLECTION)]
      {::u/collection (map entry->item (entries-z collection-z))})
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
  [config]
  (->
   (std/spec
    {:name ::nml
     :spec nml})
   (assoc :encode/xml (partial library->nml config))))

(s/fdef library->nml
  :args (s/cat :config ::config/config :library-spec any? :library u/library-spec)
  :ret (nml-spec {})
  :fn (fn equiv-collection-counts? [{{conformed-library :library} :args conformed-nml :ret}]
        (let [library (s/unform u/library-spec conformed-library)
              nml-z (zip/xml-zip (s/unform (nml-spec {}) conformed-nml))
              collection-z (zx/xml1-> nml-z :COLLECTION)]
          (= (count (->> library ::u/collection))
             (count (zx/xml-> collection-z :ENTRY))))))

(def library-spec
  (-> u/library-spec
      (assoc :decode/xml nml->library)))
