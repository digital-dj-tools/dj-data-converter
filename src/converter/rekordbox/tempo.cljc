#?(:clj (set! *warn-on-reflection* true))
(ns converter.rekordbox.tempo
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.data.zip.xml :as zx]
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
  :ret ut/tempo-spec
  :fn (fn equiv-tempo? [{{conformed-tempo :tempo} :args conformed-item-tempo :ret}]
        (let [tempo-z (zip/xml-zip (s/unform tempo-spec conformed-tempo))
              item-tempo (s/unform ut/tempo-spec conformed-item-tempo)]
          (and
           (= (zx/attr tempo-z :Inizio) (::ut/inizio item-tempo))
           (= (zx/attr tempo-z :Bpm) (::ut/bpm item-tempo))))))

(defn tempo->item-tempo
  [tempo-z]
  (-> tempo-z
      zip/node
      :attrs
      (map/transform-keys (comp #(keyword (namespace ::ut/unused) %) csk/->kebab-case-string name))))

(s/fdef item-tempo->tempo
  :args (s/cat :item-tempo ut/tempo-spec)
  :ret tempo-spec
  :fn (fn equiv-tempo? [{{conformed-item-tempo :item-tempo} :args conformed-tempo :ret}]
        (let [item-tempo (s/unform ut/tempo-spec conformed-item-tempo)
              tempo-z (zip/xml-zip (s/unform tempo-spec conformed-tempo))]
          (and
           (= (::ut/inizio item-tempo) (zx/attr tempo-z :Inizio))
           (= (::ut/bpm item-tempo) (zx/attr tempo-z :Bpm))))))

(defn item-tempo->tempo
  [item-tempo]
  {:tag :TEMPO
   :attrs (map/transform-keys item-tempo csk/->PascalCaseKeyword)})