(ns converter.universal.marker
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.map :as map]
   [converter.spec :as spec]
   [spec-tools.data-spec :as std]))

(s/def ::type-kw
  (s/spec #{::type-cue
            ::type-fade-in
            ::type-fade-out
            ::type-load
            ::type-grid
            ::type-loop}))

(defn end-after-start
  [marker]
  (update marker ::end (fn [end start] (+ start end)) (::start marker)))

(defn only-grid-markers-have-num-minus-one
  [marker]
  (if (= "-1" (::num marker))
    (assoc marker ::type ::type-grid)
    marker))

(defn end-equals-start-except-for-loop-markers
  [marker]
  (if (not= ::type-loop (::type marker))
    (assoc marker ::end (::start marker))
    marker))

(def marker {::name string?
             ::type ::type-kw
             ::start ::spec/not-neg-double ; seconds
             ::end ::spec/not-neg-double ; seconds
             ::num (s/spec #{"-1" "0" "1" "2" "3" "4" "5" "6" "7"})})

(def marker-spec
  (as->
   (std/spec
    {:name ::marker
     :spec marker})
   $
    (assoc $ :gen (fn [] (gen/fmap #((comp end-equals-start-except-for-loop-markers
                                           only-grid-markers-have-num-minus-one
                                           end-after-start) %) (s/gen $))))))