(ns converter.rekordbox.core
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.data.zip.xml :as zx]
   [clojure.zip :as zip]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.rekordbox.position-mark :as rp]
   [converter.rekordbox.tempo :as rt]
   [converter.spec :as spec]
   [converter.url :as url]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]
   [utils.map :as map]))

(def track-spec
  (std/spec
   {:name ::track
    :spec {:tag (s/spec #{:TRACK})
           :attrs {:Location ::url/url
                   :TotalTime string?
                   (std/opt :Name) string?
                   (std/opt :Artist) string?
                   (std/opt :Album) string?
                   (std/opt :AverageBpm) (s/double-in :min 0 :NaN? false :infinite? false)}
           :content (s/cat
                     :tempos (s/* (std/spec {:name ::tempo
                                             :spec rt/tempo-spec}))
                     :position-marks (s/* (std/spec {:name ::position-mark
                                                     :spec rp/position-mark-spec})))}}))

(defn equiv-position-marks?
  [{:keys [::u/markers]} track-z]
  (let [non-grid-markers (remove um/grid-marker? markers)]
    (every? identity
            (map
             #(and
               (= (::um/num %1) (zx/attr (first %2) :Num))
               (= "-1" (zx/attr (second %2) :Num)))
             non-grid-markers
             (partition 2 (zx/xml-> track-z :POSITION_MARK))))))

(s/fdef item->track
  :args (s/cat :item (spec/such-that-spec u/item-spec #(contains? % ::u/total-time) 100))
  :ret track-spec
  :fn (fn equiv-track? [{{conformed-item :item} :args conformed-track :ret}]
        (let [item (s/unform u/item-spec conformed-item)
              track-z (zip/xml-zip (s/unform track-spec conformed-track))]
          (and
           (= (::u/title item) (zx/attr track-z :Name))
           (= (::u/artist item) (zx/attr track-z :Artist))
           (equiv-position-marks? item track-z))))) ; TODO tempos

(defn item->track
  [{:keys [::u/title ::u/bpm ::u/markers ::u/tempos] :as item}]
  {:tag :TRACK
   :attrs
   (cond-> item
     true (-> (dissoc ::u/title ::u/bpm ::u/markers ::u/tempos) (map/transform-keys csk/->PascalCaseKeyword))
     title (assoc :Name title)
     bpm (assoc :AverageBpm bpm))
   :content (let [non-grid-markers (remove um/grid-marker? markers)]
              (cond-> []
                tempos (concat (map rt/item-tempo->tempo tempos))
                ; two position marks for each marker, one is a hotcue, the other is a memory cue
                non-grid-markers (concat (reduce #(conj %1
                                                        (rp/marker->position-mark %2 false)
                                                        (rp/marker->position-mark %2 true))
                                                 []
                                                 non-grid-markers))))})

(s/fdef track->item
  :args (s/cat :track (spec/xml-zip-spec track-spec))
  :ret u/item-spec
  :fn (fn equiv-item? [{{conformed-track :track} :args conformed-item :ret}]
        (let [track-z (zip/xml-zip (s/unform track-spec conformed-track))
              item (s/unform u/item-spec conformed-item)]
          (and
           (= (zx/attr track-z :Name) (::u/title item))
           (= (zx/attr track-z :Artist) (::u/artist item))))))

(defn track->item
  [track-z]
  (let [tempo-z (zx/xml-> track-z :TEMPO)
        Name (zx/attr track-z :Name)
        AverageBpm (zx/attr track-z :AverageBpm)]
    (cond-> track-z
      true (-> first :attrs (dissoc :Name :AverageBpm) (map/transform-keys (comp #(keyword (namespace ::u/unused) %) csk/->kebab-case name)))
      Name (assoc ::u/title Name)
      AverageBpm (assoc ::u/bpm AverageBpm)
      (not-empty tempo-z) (assoc ::u/tempos (map rt/tempo->item-tempo tempo-z)))))

(defn library->dj-playlists
  [progress _ {:keys [::u/collection]}]
  {:tag :DJ_PLAYLISTS
   :attrs {:Version "1.0.0"}
   :content [{:tag :COLLECTION
              :content (map (if progress (progress item->track) item->track)
                            (remove #(not (contains? % ::u/total-time)) collection))}]})

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
  ([]
   (dj-playlists-spec nil))
  ([progress]
   (->
    (std/spec
     {:name ::dj-playlists
      :spec dj-playlists})
    (assoc :encode/xml (partial library->dj-playlists progress)))))

(s/fdef library->dj-playlists
  :args (s/cat :progress nil? :library-spec any? :library u/library-spec)
  :ret dj-playlists-spec
  :fn (fn equiv-collection-counts? [{{conformed-library :library} :args conformed-dj-playlists :ret}]
        (let [library (s/unform u/library-spec conformed-library)
              dj-playlists (s/unform dj-playlists-spec conformed-dj-playlists)]
          (= (count (->> library ::u/collection (remove #(not (contains? % ::u/total-time)))))
             (count (->> dj-playlists :content first :content))))))

(def library-spec
  (-> u/library-spec
      (assoc :decode/xml dj-playlists->library)))