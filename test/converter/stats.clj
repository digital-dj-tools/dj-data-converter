(ns converter.stats
  (:require [clojure.core.matrix.stats :as stats]
            [converter.universal.core :as u]
            [converter.universal.tempo :as ut]))

(defn mean-tempos
  [library]
  (double (stats/mean (map (comp count ::u/tempos) (::u/collection library)))))

(defn count-tempos-of-rand-n-items
  [library n]
  (map (comp count ::u/tempos) (take n (shuffle (::u/collection library)))))

(defn max-tempos
  [library]
  (apply max (map (comp count ::u/tempos) (::u/collection library))))

(defn tempo-battitos-of-rand-n-items
  [library n]
  (map #(map ::ut/battito (::u/tempos %)) (take n (shuffle (::u/collection library)))))

(defn mean-markers
  [library]
  (double (stats/mean (map (comp count ::u/markers) (::u/collection library)))))