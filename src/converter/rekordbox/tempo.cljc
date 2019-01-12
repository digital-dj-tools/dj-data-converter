(ns converter.rekordbox.tempo
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.map :as map]
   [converter.spec :as spec]
   [spec-tools.data-spec :as std]))

(def tempo-spec
  (std/spec
   {:name ::tempo
    :spec {:tag (s/spec #{:TEMPO})
           :attrs {:Inizio (s/double-in :min 0 :max 3600 :NaN? false :infinite? false)
                   :Bpm string?
                   :Metro string?
                   :Battito string?}}}))

(defn item-tempo->tempo
  [item-tempo]
  {:tag :TEMPO
   :attrs (map/transform-keys item-tempo csk/->PascalCaseKeyword)})