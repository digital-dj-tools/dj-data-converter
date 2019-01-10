(ns converter.rekordbox.core
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.map :as map]
   [converter.universal.core :as u]
   [converter.rekordbox.position-mark :as rp]
   [converter.rekordbox.tempo :as rt]
   [converter.spec :as spec]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]))

(def track-spec
  (std/spec
   {:name ::track
    :spec {:tag (s/spec #{:TRACK})
           :attrs {:Location ::spec/url
                   :TotalTime string?
                   (std/opt :Name) string?
                   (std/opt :Artist) string?
                   (std/opt :Album) string?
                   (std/opt :AverageBpm) string?}
           :content (s/cat
                     :tempo (s/* (std/spec {:name ::tempo
                                            :spec rt/tempo-spec}))
                     :position-mark (s/* (std/spec {:name ::position-mark
                                                    :spec rp/position-mark-spec})))}}))

(s/fdef item->track
  :args (s/cat :item (spec/such-that-spec u/item-spec #(contains? % ::u/total-time) 100))
  :fn (fn equiv-track? [{{conformed-item :item} :args conformed-track :ret}]
        (let [item (s/unform u/item-spec conformed-item)
              track (s/unform track-spec conformed-track)]
          (and
           (= (::u/title item) (-> track :attrs :Name))
           (= (::u/artist item) (-> track :attrs :Artist))))) ; TODO tempos and position marks
  :ret track-spec)

(defn item->track
  [{:keys [::u/title ::u/bpm ::u/tempos ::u/markers] :as item}]
  {:tag :TRACK
   :attrs
   (cond-> item
     true (-> (dissoc ::u/title ::u/bpm ::u/tempos ::u/markers) (map/transform-keys csk/->PascalCaseKeyword))
     title (assoc :Name title)
     bpm (assoc :AverageBpm bpm))
   :content (cond-> []
              tempos (concat (map rt/item-tempo->tempo tempos))
              markers (concat (map rp/marker->position-mark markers)))})

(defn library->dj-playlists
  [_ {:keys [::u/collection]}]
  {:tag :DJ_PLAYLISTS
   :attrs {:Version "1.0.0"}
   :content [{:tag :COLLECTION
              :content (map item->track (remove #(not (contains? % ::u/total-time)) collection))}]})

(defn dj-playlists->library
  [_ dj-playlists])

(def dj-playlists
  {:tag (s/spec #{:DJ_PLAYLISTS})
   :attrs {:Version string?}
   :content (s/cat
             :collection (std/spec
                          {:name ::collection
                           :spec {:tag (s/spec #{:COLLECTION})
                                  :content (s/cat :tracks (s/* track-spec))}}))})

(def dj-playlists-spec
  (->
   (std/spec
    {:name ::dj-playlists
     :spec dj-playlists})
   (assoc
    :encode/xml library->dj-playlists)))

(s/fdef library->dj-playlists
  :args (s/cat :library-spec any? :library u/library-spec)
  :ret dj-playlists-spec
  :fn (fn equiv-collection-counts? [{{conformed-library :library} :args conformed-dj-playlists :ret}]
        (let [library (s/unform u/library-spec conformed-library)
              dj-playlists (s/unform dj-playlists-spec conformed-dj-playlists)]
          (= (count (->> library ::u/collection (remove #(not (contains? % ::u/total-time)))))
             (count (->> dj-playlists :content first :content))))))