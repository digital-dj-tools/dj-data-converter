(ns converter.rekordbox.tempo
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.zip :as zip]
   [converter.spec :as spec]
   [converter.universal.tempo :as ut]
   [spec-tools.data-spec :as std]
   [utils.map :as map]))

(def tempo-spec
  (std/spec
   {:name ::tempo
    :spec {:tag (s/spec #{:TEMPO})
           :attrs {:Inizio (s/double-in :min 0 :max 7200 :NaN? false :infinite? false)
                   :Bpm (s/double-in :min 0 :NaN? false :infinite? false)
                   :Metro string?
                   :Battito string?}}}))

(s/fdef tempo->item-tempo
  :args (s/cat :tempo (spec/xml-zip-spec tempo-spec))
  :ret ut/tempo-spec)

(defn tempo->item-tempo
  [tempo-z]
  (-> tempo-z
      zip/node
      :attrs
      (map/transform-keys (comp #(keyword (namespace ::ut/unused) %) csk/->kebab-case-string name))))

(defn item-tempo->tempo
  [item-tempo]
  {:tag :TEMPO
   :attrs (map/transform-keys item-tempo csk/->PascalCaseKeyword)})