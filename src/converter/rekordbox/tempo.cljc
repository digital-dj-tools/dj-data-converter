(ns converter.rekordbox.tempo
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.zip :as zip]
   [converter.map :as map]
   [converter.spec :as spec]
   [converter.universal.tempo :as ut]
   [spec-tools.data-spec :as std]))

(def tempo-spec
  (std/spec
   {:name ::tempo
    :spec {:tag (s/spec #{:TEMPO})
           :attrs {:Inizio (s/double-in :min 0 :max 7200 :NaN? false :infinite? false)
                   :Bpm (s/double-in :min 0 :NaN? false :infinite? false)
                   :Metro string?
                   :Battito string?}}}))

(defn item-tempo-inizio->tempo-inizio
  [{:keys [::ut/inizio]} tempo _]
  (let [inizio-shift (+ inizio -0.000)]
    (assoc tempo :Inizio (if (neg? inizio-shift) 0 inizio-shift))))

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
   :attrs (map/transform item-tempo
                         (partial map/transform-key csk/->PascalCaseKeyword)
                         {::ut/inizio item-tempo-inizio->tempo-inizio})})