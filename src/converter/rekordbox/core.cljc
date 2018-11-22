(ns converter.rekordbox.core
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.map :as map]
   [converter.rekordbox.position-mark :as rp]
   [converter.rekordbox.tempo :as rt]
   [converter.spec :as spec]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]))

(def track-xml-spec
  (std/spec
   {:name ::track-xml
    :spec {:tag (s/spec #{:TRACK})
           :attrs {:Location ::spec/url
                   :TotalTime string?
                   (std/opt :Name) string?
                   (std/opt :Artist) string?
                   (std/opt :Album) string?
                   (std/opt :AverageBpm) string?}
           :content (s/cat 
                     :tempo-xml (s/* (std/spec {:name ::tempo-xml
                                                :spec rt/tempo-xml-spec}))
                     :position-mark-xml (s/* (std/spec {:name ::position-mark-xml
                                                        :spec rp/position-mark-xml-spec})))}}))

(def track
  {::location ::spec/url
   ::total-time string?
   (std/opt ::name) string?
   (std/opt ::artist) string?
   (std/opt ::album) string?
   (std/opt ::average-bpm) string?
   (std/opt ::tempos) [rt/tempo-spec]  ; how can I say, coll must not be empty?
   (std/opt ::position-marks) [rp/position-mark-spec]  ; how can I say, coll must not be empty?
   })

(def track-spec
  (std/spec
   {:name ::track
    :spec track}))

; TODO implement :fn check
(s/fdef track->xml
  :args (s/cat :entry track-spec)
  :ret track-xml-spec)

(defn track->xml
  [{:keys [::tempos ::position-marks] :as track}]
  {:tag :TRACK
   :attrs (map/transform-keys (dissoc track ::tempos ::position-marks) csk/->PascalCaseKeyword)
   :content (cond-> []
              tempos (concat (map rt/tempo->xml tempos))
              position-marks (concat (map rp/position-mark->xml position-marks)))})

(defn dj-playlists->xml
  [_ dj-playlists]
  {:tag :DJ_PLAYLISTS
   :attrs {:Version "1.0.0"}
   :content [{:tag :COLLECTION :content (map track->xml (::collection dj-playlists))}]})

(defn xml->dj-playlists
  [_ xml])

(def dj-playlists-xml-spec
  (->
   (std/spec
    {:name ::dj-playlists-xml
     :spec {:tag (s/spec #{:DJ_PLAYLISTS})
            :attrs {:Version string?}
            :content (s/cat
                      :collection-xml (std/spec
                                       {:name ::collection-xml
                                        :spec {:tag (s/spec #{:COLLECTION})
                                               :content (s/cat :tracks-xml (s/* track-xml-spec))}}))}})
   (assoc
    :encode/xml dj-playlists->xml)))

(def dj-playlists
  {::collection (s/cat :tracks (s/* track-spec))})

(def dj-playlists-spec
  (->
   (std/spec
    {:name ::dj-playlists
     :spec dj-playlists})
   (assoc
    :decode/xml xml->dj-playlists)))