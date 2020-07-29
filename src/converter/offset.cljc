(set! *warn-on-reflection* true)
(ns converter.offset
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.config :as config]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [mp3-parser.lame :as mp3lame]
   [spec-tools.data-spec :as std]))

(defn- calculate
  [{:keys [xing-tag? lame-tag?] {:keys [::mp3lame/crc-valid?]} :lame}]
  (->>
   (cond
     (not xing-tag?) ["A" 0]
     (not lame-tag?) ["B" 0.026]
     (not crc-valid?) ["C" 0.026]
     :else ["D" 0])
   (interleave [:bucket :value])
   (apply hash-map)))

(def offset
  {:bucket (s/spec #{"A" "B" "C" "D"})
   :value (s/spec #{0 0.026})})

(def offset-spec
  (std/spec
  {:name ::offset
   :spec offset}))

(def offset-signed-spec
  (std/spec
   {:name ::offset-signed
    :spec (-> offset 
              (assoc :value-signed (s/spec #{0 0.026 -0.026}))
              (dissoc :value))}))

(s/fdef signed-value
  :args (s/cat :config ::config/config :offset offset-spec)
  :ret offset-signed-spec
  :fn (fn correct-sign? 
        [{{conformed-config :config} :args conformed-offset-signed :ret}]
        (let [config (s/unform ::config/config conformed-config)
              offset-signed (s/unform offset-signed-spec conformed-offset-signed)]
          (cond
            (config/traktor->rekordbox? config) (not (pos? (:value-signed offset-signed)))
            true (not (neg? (:value-signed offset-signed)))))))

(defn- signed-value
  [config {:keys [value] :as offset}]
  (-> offset
      (dissoc :value)
      (cond->
        (config/traktor->rekordbox? config) (assoc :value-signed (- value))
        (not (config/traktor->rekordbox? config)) (assoc :value-signed value))))

(defn- correct-tempo
  [offset tempo]
  (update tempo ::ut/inizio #(+ % (:value-signed offset))))

(defn- correct-marker
  [{:keys [:value-signed]} marker]
  (-> marker
      (update ::um/start #(+ % value-signed))
      (update ::um/end #(+ % value-signed))))

(defn correct
  [config item mp3-parsed]
  (let [offset (calculate mp3-parsed)
        offset-signed (signed-value config offset)]
    (-> item
        (update ::u/tempos #(map (partial correct-tempo offset-signed) %))
        (update ::u/markers #(map (partial correct-marker offset-signed) %)))))
