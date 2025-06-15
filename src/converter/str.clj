(set! *warn-on-reflection* true)
(ns converter.str
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as string]))

(defn not-blank-string-gen
  []
  (gen/such-that #(not (string/blank? %)) (gen/string-alphanumeric)))

(s/def ::not-blank-string
  (s/with-gen
    (s/and string? #(not (string/blank? %)))
    (fn [] (not-blank-string-gen))))

(defn not-blank-string-with-whitespace-gen
  []
  (->> (gen/string-alphanumeric)
       (gen/such-that #(not (string/blank? %)))
       (gen/fmap (fn [s] (apply str
                                (map-indexed #(if (and (< 0 %1) (< %1 (dec (count s))))
                                                (rand-nth (conj (repeat 9 %2) " "))
                                                (identity %2))
                                             s))))))

(s/def ::not-blank-string-with-whitespace
  (s/with-gen
    (s/and string? #(not (string/blank? %)))
    (fn [] (not-blank-string-with-whitespace-gen))))

(def drive-letter-regex #"[A-Z]:")

(defn drive-letter?
  [str]
  (if (string? str)
    (boolean (re-matches drive-letter-regex str))
    false))

(defn drive-letter-gen
  []
  (gen/fmap #(-> % string/upper-case (str ":")) (gen/char-alpha)))

(s/def ::drive-letter
  (s/with-gen
    (s/and string? drive-letter?)
    (fn [] (drive-letter-gen))))