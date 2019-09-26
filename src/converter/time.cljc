(ns converter.time
  (:require
   [cljc.java-time.local-date :as jtld]
   [cljc.java-time.format.date-time-formatter :as jtf]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   #?(:cljs [converter.js-joda])
   [spec-tools.core :as st]
   [tick.alpha.api :as t]
   [tick.format :as tf]
   #?(:cljs [java.time :refer [Instant Clock LocalDate]]))
  #?(:clj
     (:import
      [java.time Instant Clock LocalDate])))

(defn instant-gen
  []
  (->> (gen/large-integer* {:min (t/long
                                  (t/instant "2000-01-01T00:00"))
                            :max (t/long
                                  (t/instant "2100-01-01T00:00"))})
       (gen/fmap #(* 1000 %))
       (gen/fmap t/instant)))

(defn instant?
  [x]
  (instance? Instant x))

(s/def ::instant (st/spec instant?
                          {:type :instant
                           :gen #(instant-gen)}))

(defn date?
  [x]
  (instance? LocalDate x))

(s/def ::date (st/spec date?
                       {:type :date
                        :gen #(gen/fmap t/date (instant-gen))}))

(defn clock-gen
  []
  (gen/fmap #(t/clock %) (instant-gen)))

(defn clock?
  [x]
  (instance? Clock x))

(s/def ::clock (s/with-gen clock? #(clock-gen)))

(defn date->string
  [format _ date]
  (t/format (tf/formatter format) date))

(defn string->date
  [format _ str]
  (if (string? str)
    (-> str
        (jtld/parse (jtf/of-pattern format)))
    str))