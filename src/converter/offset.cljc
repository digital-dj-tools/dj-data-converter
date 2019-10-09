(ns converter.offset
  (:require
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [mp3-parser.lame :as mp3lame]))

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

(defn- correct-tempo
  [offset tempo]
  (update tempo ::ut/inizio #(- % (:value offset))))

(defn- correct-marker
  [offset marker]
  (-> marker
      (update ::um/start #(- % (:value offset)))
      (update ::um/end #(- % (:value offset)))))

(defn correct
  [item mp3-parsed]
  (let [offset (calculate mp3-parsed)]
    (-> item
        (update ::u/tempos #(map (partial correct-tempo offset) %))
        (update ::u/markers #(map (partial correct-marker offset) %)))))
