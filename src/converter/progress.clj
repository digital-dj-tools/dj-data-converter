(set! *warn-on-reflection* true)
(ns converter.progress)

(defn dots-println
  [e f]
  (let [item-count (atom 1)]
    (fn [item]
      (when (= 0 (mod @item-count e))
        (println ".")
        (flush))
      (swap! item-count inc)
      (f item))))

(defn dots-print
  [e f]
  (let [item-count (atom 1)]
    (fn [item]
      (when (= 0 (mod @item-count e))
        (print "."))
      (swap! item-count inc)
      (f item))))
