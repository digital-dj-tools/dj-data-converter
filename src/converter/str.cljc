(ns converter.str
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as str]))

(defn not-blank-string-gen
  []
  (gen/such-that #(not (str/blank? %)) (gen/string-alphanumeric)))

(defn not-blank-string-with-whitespace-gen
  []
  (->> (gen/string-alphanumeric)
       (gen/such-that #(not (str/blank? %)))
       (gen/fmap (fn [s] (apply str
                                (map-indexed #(if (and (< 0 %1) (< %1 (dec (count s))))
                                                (rand-nth (conj (repeat 9 %2) " "))
                                                (identity %2))
                                             s))))))

(s/def ::not-blank-string
  (s/with-gen
    (s/and string? #(not (str/blank? %)))
    (fn [] (not-blank-string-gen))))

(def drive-letter-regex #"[A-Z]:")

(defn drive-letter?
  [str]
  (if (string? str)
    (boolean (re-matches drive-letter-regex str))
    false))

(defn drive-letter-gen
  []
  (gen/fmap #(-> % str/upper-case (str ":")) (gen/char-alpha)))

(s/def ::drive-letter
  (s/with-gen
    (s/and string? drive-letter?)
    (fn [] (drive-letter-gen))))