(ns converter.rekordbox.core
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.data.zip.xml :as zx]
   [clojure.zip :as zip]
   [converter.config :as config]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.rekordbox.position-mark :as rp]
   [converter.rekordbox.tempo :as rt]
   [converter.spec :as spec]
   [converter.time :as time]
   [converter.url :as url]
   [converter.xml :as xml]
   [spec-tools.data-spec :as std]
   [utils.map :as map]
   #?(:clj [taoensso.tufte :as tufte :refer (defnp p profile)]
      :cljs [taoensso.tufte :as tufte :refer-macros (defnp p profile)])))

(def xml-transformer
  (spec/xml-transformer))

(def string-transformer
  (spec/string-transformer))

(def track-spec
  (std/spec
   {:name ::track
    :spec {:tag (s/spec #{:TRACK})
           :attrs {:Location ::url/url
                   :TotalTime string?
                   (std/opt :TrackID) pos-int?
                   (std/opt :Name) string?
                   (std/opt :Artist) string?
                   (std/opt :Album) string?
                   (std/opt :Genre) string?
                   (std/opt :TrackNumber) string?
                   (std/opt :AverageBpm) (s/double-in :min 0 :NaN? false :infinite? false)
                   (std/opt :DateAdded) ::time/date
                   (std/opt :Comments) string?}
           :content (s/cat
                     :tempos (s/* (std/spec {:name ::tempo
                                             :spec rt/tempo-spec}))
                     :position-marks (s/* (std/spec {:name ::position-mark
                                                     :spec rp/position-mark-spec})))}}))

(defn equiv-position-marks?
  [{:keys [::u/markers]} track-z]
  (let [indexed-markers (um/indexed-markers markers)
        non-indexed-markers (um/non-indexed-markers markers)
        position-marks-z (zx/xml-> track-z :POSITION_MARK)
        position-marks-hot-cue-z (remove (comp rp/memory-cue? zip/node) position-marks-z)
        position-marks-memory-cue-z (filter (comp rp/memory-cue? zip/node) position-marks-z)
        position-marks-tagged-z (filter (comp rp/position-mark-tagged? zip/node) position-marks-z)]
    (and
     (= (count indexed-markers) (count position-marks-hot-cue-z))
     (= (count non-indexed-markers) (- (count position-marks-memory-cue-z) (count position-marks-tagged-z)))
     (every? identity
             (map
              #(= (::um/num %1) (zx/attr %2 :Num))
              indexed-markers
              position-marks-hot-cue-z)))))

(s/fdef item->track
  :args (s/cat :item (spec/such-that-spec u/item-spec u/item-contains-total-time? 100))
  :ret track-spec
  :fn (fn equiv-track? [{{conformed-item :item} :args conformed-track :ret}]
        (let [item (s/unform u/item-spec conformed-item)
              track-z (zip/xml-zip (s/unform track-spec conformed-track))]
          (and
           (= (::u/title item) (zx/attr track-z :Name))
           (= (::u/artist item) (zx/attr track-z :Artist))
           (= (::u/total-time item) (zx/attr track-z :TotalTime))
           (= (::u/comments item) (zx/attr track-z :Comments))
           (= (::u/genre item) (zx/attr track-z :Genre))
           (equiv-position-marks? item track-z))))) ; TODO equiv-track-tempos

; TODO move to position-mark ns?
(defn marker->position-marks
  [non-indexed-markers marker]
  (cond-> []
    true (conj (rp/marker->position-mark marker (um/non-indexed-marker? marker)))
    (and (not (um/non-indexed-marker? marker))
         (not (um/matching-marker? non-indexed-markers marker))) (conj (rp/marker->position-mark-tagged marker true))))

(defn item->track
  [{:keys [::u/title ::u/bpm ::u/markers ::u/tempos] :as item}]
  (p ::item->track
     {:tag :TRACK
      :attrs (cond-> item
               true (-> (dissoc ::u/title ::u/bpm ::u/markers ::u/tempos) (map/transform-keys csk/->PascalCaseKeyword))
               title (assoc :Name title)
               bpm (assoc :AverageBpm bpm))
      :content (cond-> []
                 tempos (concat (map rt/item-tempo->tempo tempos))
                 markers (concat (reduce #(concat %1 (marker->position-marks (um/non-indexed-markers markers) %2)) [] markers)))}))

(defn equiv-markers?
  [track-z {:keys [::u/markers]}]
  (let [position-marks-z (remove (comp rp/position-mark-tagged? zip/node) (zx/xml-> track-z :POSITION_MARK))]
    (= (count position-marks-z) (count markers))))

(defn equiv-tempos?
  [track-z {:keys [::u/tempos]}]
  (let [tempos-z (zx/xml-> track-z :TEMPO)]
    (= (count tempos-z) (count tempos))))

(s/fdef track->item
  :args (s/cat :track (spec/xml-zip-spec track-spec))
  :ret u/item-spec
  :fn (fn equiv-item? [{{conformed-track :track} :args conformed-item :ret}]
        (let [track-z (zip/xml-zip (s/unform track-spec conformed-track))
              item (s/unform u/item-spec conformed-item)]
          (and
           (= (zx/attr track-z :Name) (::u/title item))
           (= (zx/attr track-z :Artist) (::u/artist item))
           (= (zx/attr track-z :Genre) (::u/genre item))
           (= (zx/attr track-z :TotalTime) (::u/total-time item))
           (= (zx/attr track-z :Comments) (::u/comments item))
           (equiv-tempos? track-z item)
           (equiv-markers? track-z item)))))

(defn track->item
  [track-z]
  (p ::track->item
     (let [tempos-z (zx/xml-> track-z :TEMPO)
           position-marks-z (remove (comp rp/position-mark-tagged? zip/node) (zx/xml-> track-z :POSITION_MARK))
           Name (zx/attr track-z :Name)
           AverageBpm (zx/attr track-z :AverageBpm)]
       (cond-> track-z
         true (-> zip/node 
                  :attrs 
                  (dissoc :Name :AverageBpm) 
                  (map/transform-keys (comp #(keyword (namespace ::u/unused) %) csk/->kebab-case name)))
         Name (assoc ::u/title Name)
         AverageBpm (assoc ::u/bpm AverageBpm)
         (not-empty tempos-z) (assoc ::u/tempos (map rt/tempo->item-tempo tempos-z))
         (not-empty position-marks-z) (assoc ::u/markers (map rp/position-mark->marker position-marks-z))))))

(defn library->dj-playlists
  [{:keys [progress]} _ {:keys [::u/collection]}]
  {:tag :DJ_PLAYLISTS
   :attrs {:Version "1.0.0"}
   :content [{:tag :COLLECTION
              :content (map (progress item->track)
                            ; Rekordbox xml tracks must have a total time
                            ; TODO summarize what was done (i.e. items without total time filtered out) in a report
                            (filter u/item-contains-total-time? collection))}]})

(defn dj-playlists->library
  [_ dj-playlists]
  (if (xml/xml? dj-playlists)
    (let [dj-playlists-z (zip/xml-zip dj-playlists)
          collection-z (zx/xml1-> dj-playlists-z :COLLECTION)]
      {::u/collection (map track->item (zx/xml-> collection-z :TRACK))})
    dj-playlists))

(def collection-spec
  (std/spec
   {:name ::collection
    :spec {:tag (s/spec #{:COLLECTION})
           :content (s/cat :tracks (s/* track-spec))}}))

(def dj-playlists
  {:tag (s/spec #{:DJ_PLAYLISTS})
   :attrs {:Version string?}
   :content (s/cat
             :product (s/? (std/spec {:name ::product
                                      :spec {:tag (s/spec #{:PRODUCT})}}))
             :collection collection-spec
             :playlists (s/? (std/spec {:name ::playlists
                                        :spec {:tag (s/spec #{:PLAYLISTS})}})))})

(defn dj-playlists-spec
  [config]
  (->
   (std/spec
    {:name ::dj-playlists
     :spec dj-playlists})
   (assoc :encode/xml (partial library->dj-playlists config))))

(s/fdef dj-playlists->library
  :args (s/cat :dj-playlists-spec any? :dj-playlists (dj-playlists-spec {}))
  :ret u/library-spec
  :fn (fn equiv-collection-counts?
        [{{conformed-dj-playlists :dj-playlists} :args conformed-library :ret}]
        (let [dj-playlists (s/unform (dj-playlists-spec {}) conformed-dj-playlists)
              dj-playlists-z (zip/xml-zip dj-playlists)
              library (s/unform u/library-spec conformed-library)]
          (=
           (count (zx/xml-> dj-playlists-z :COLLECTION :TRACK))
           (count (::u/collection library))))))

(s/fdef library->dj-playlists
  :args (s/cat :config ::config/config :library-spec any? :library u/library-spec)
  :ret (dj-playlists-spec {})
  :fn (fn equiv-collection-counts?
        [{{conformed-library :library} :args conformed-dj-playlists :ret}]
        (let [library (s/unform u/library-spec conformed-library)
              dj-playlists (s/unform (dj-playlists-spec {}) conformed-dj-playlists)
              dj-playlists-z (zip/xml-zip dj-playlists)]
          (= (count (->> library ::u/collection (filter u/item-contains-total-time?)))
             (count (zx/xml-> dj-playlists-z :COLLECTION :TRACK))))))

(def library-spec
  (-> u/library-spec
      (assoc :decode/xml dj-playlists->library)))