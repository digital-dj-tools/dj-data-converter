(set! *warn-on-reflection* true)
(ns converter.universal.marker
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
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
                                           end-for-loop-markers) %) (s/gen $))))))

(defn marker-of-type?
  [marker & marker-types]
  (contains? (set marker-types) (::type marker)))

(defn non-indexed-marker?
  [marker]
  (= "-1" (::num marker)))

(defn non-indexed-markers
  [markers]
  (filter non-indexed-marker? markers))

(defn indexed-markers
  [markers]
  (remove non-indexed-marker? markers))

(defn- matching-markers?
  "Returns true if the markers are matching (on type, start and end)."
  [m1 m2]
  (let [keys [::type ::start ::end]]
    (= (select-keys m1 keys)
       (select-keys m2 keys))))

(defn matching-marker?
  "Returns true if markers has at least one marker matching the marker."
  [markers marker]
  (some #(matching-markers? % marker) markers))

(defn non-indexed-markers-without-matching-indexed-marker
  "Returns the non-indexed markers that don't have a matching indexed marker."
  [markers]
  (remove (partial matching-marker? (indexed-markers markers))
          (non-indexed-markers markers)))