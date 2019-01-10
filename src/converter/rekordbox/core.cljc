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

; (def track
;   {::location ::spec/url
;    ::total-time string?
;    (std/opt ::name) string?
;    (std/opt ::artist) string?
;    (std/opt ::album) string?
;    (std/opt ::average-bpm) string?
;    (std/opt ::tempos) [rt/tempo-spec]  ; how can I say, coll must not be empty?
;    (std/opt ::position-marks) [rp/position-mark-spec]  ; how can I say, coll must not be empty?
;    })

; (def track-spec
;   (std/spec
;    {:name ::track
;     :spec track}))

; TODO implement :fn check
(s/fdef item->track
  :args (s/cat :entry (spec/such-that-spec u/item-spec #(contains? % ::u/total-time) 100))
  :ret track-spec)

(defn item->track
  [{:keys [::u/bpm ::u/tempos ::u/markers] :as item}]
  {:tag :TRACK
   :attrs
   (cond-> item
     true (-> (dissoc ::u/bpm ::u/tempos ::u/markers) (map/transform-keys csk/->PascalCaseKeyword))
     bpm (assoc :AverageBpm bpm))
   :content (cond-> []
              tempos (concat (map rt/item-tempo->tempo tempos))
              markers (concat (map rp/marker->position-mark markers)))})

(defn library->dj-playlists
  [_ library]
  {:tag :DJ_PLAYLISTS
   :attrs {:Version "1.0.0"}
   :content [{:tag :COLLECTION :content (map item->track (::u/collection library))}]}) ; TODO items without playtime should be removed

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