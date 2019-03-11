(ns converter.universal.marker
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.spec :as spec]
   [spec-tools.data-spec :as std]))

(s/def ::type-kw
  (s/spec #{::type-cue
            ::type-fade-in
            ::type-fade-out
            ::type-load
            ::type-grid
            ::type-loop}))

(defn end-for-loop-markers
  [marker]
  (if (= ::type-loop (::type marker))
    (update marker ::end (fn [end start] (if (< 7200 (+ start end)) 7200 (+ start end))) (::start marker))
    marker))

(defn only-grid-markers-have-num-minus-one
  [marker]
  (if (= "-1" (::num marker))
    (assoc marker ::type ::type-grid)
    marker))

(defn end-for-other-markers
  [marker]
  (if (not= ::type-loop (::type marker))
    (assoc marker ::end (::start marker))
    marker))

(def marker {::name string?
             ::type ::type-kw
             ::start (s/double-in :min 0 :max 7200 :NaN? false :infinite? false) ; seconds
             ::end (s/double-in :min 0 :max 7200 :NaN? false :infinite? false) ; seconds
             ::num (s/spec #{"-1" "0" "1" "2" "3" "4" "5" "6" "7"})})

(def marker-spec
  (as->
   (std/spec
    {:name ::marker
     :spec marker})
   $
    (assoc $ :gen (fn [] (gen/fmap #((comp end-for-other-markers
                                           only-grid-markers-have-num-minus-one
                                           end-for-loop-markers) %) (s/gen $))))))