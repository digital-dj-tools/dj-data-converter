(ns converter.time
  (:require
   [cljc.java-time.local-date :as jtld]
   [cljc.java-time.format.date-time-formatter :as jtf]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as string]
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

(defn date-str?
  [format x]
  (try (do
         (jtld/parse x (jtf/of-pattern format))
         true)
       #?(:clj (catch Throwable t false)
          :cljs (catch :default e false))))

(defn date-str-spec
  [format]
  (st/spec (partial date-str? format)
           {:type :string
            :gen #(gen/fmap (comp (partial t/format (tf/formatter format)) t/date) (instant-gen))}))

(s/def ::seconds-per-day
  (s/int-in 0 86400))

(defn clock-gen
  []
  (gen/fmap #(t/clock %) (instant-gen)))

(defn clock?
  [x]
  (instance? Clock x))

(s/def ::clock (s/with-gen clock? #(clock-gen)))

(defn date->string
  [format _ x]
  (if (date? x)
    (t/format ((memoize tf/formatter) format) x)
    x))

(defn string->date
  [format _ x]
  (if (and (string? x) (not (string/blank? x)))
    (jtld/parse x ((memoize jtf/of-pattern) format))
    x))
