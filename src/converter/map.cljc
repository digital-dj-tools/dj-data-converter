(ns converter.map
    (:require [clojure.walk :as walk]))

(defn transform-key
  [f m1 m2 k]
  (assoc m2 (f k) (k m1)))

(defn transform-key-ns
  [ns m1 m2 k]
  (assoc m2 (keyword ns (name k)) (k m1)))

(defn transform
  [m f e]
  (reduce #(if-let [f-e (%2 e)]
             (if-let [k-e (keyword f-e)]
               (assoc %1 k-e (%2 m))
               (f-e m %1 %2))
             (f m %1 %2)) {} (keys m)))

; https://stackoverflow.com/a/21483588
(defn transform-keys
  [m f]
  (into {} (map #(update-in % [0] f) m)))

; https://stackoverflow.com/questions/29362150/remove-nil-values-from-deeply-nested-maps
(defn remove-nil
  "remove pairs of key-value where value is nil from a (possibly nested) map."
  [m & ks]
  (walk/postwalk
   #(if (and (not (record? %)) (map? %))
      (into {} (remove (fn [[k v]] (and
                                    (or (empty? ks) (contains? (set ks) k))
                                    (nil? v))) %))
      %)
   m))

(defn remove-empty
  "remove pairs of key-value where value is an empty coll from a (possibly nested) map."
  [m & ks]
  (walk/postwalk
   #(if (and (not (record? %)) (map? %))
      (into {} (remove (fn [[k v]] (and
                                    (or (empty? ks) (contains? (set ks) k))
                                    (coll? v)
                                    (empty? v))) %))
      %)
   m))

; https://stackoverflow.com/questions/19150172/deep-reverse-clojure
(defn reverse
  [m & ks]
  (clojure.walk/postwalk
   #(if (and (not (record? %)) (map? %))
      (into {} (map (fn [[k v]] 
                      (if (and (or (empty? ks) (contains? (set ks) k)) (seq? v)) 
                        [k (clojure.core/reverse v)] 
                        [k v])) %))
      %)
   m))

(defn equiv-key-ns
  [ns m1 m2 k]
  (= (k m1) ((keyword ns (name k)) m2)))

(defn equiv?
  [m1 m2 f e]
  (every? identity (map #(if-let [f-e (% e)]
                           (if-let [k-e (keyword f-e)]
                             (= (% m1) (k-e m2))
                             (f-e m1 m2 %))
                           (f m1 m2 %)) (keys m1))))