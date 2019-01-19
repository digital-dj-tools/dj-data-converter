(ns converter.universal.core
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.map :as map]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.spec :as spec]
   [converter.url :as url]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]))

(def item
  {::location ::url/url
   (std/opt ::title) string?
   (std/opt ::artist) string?
   (std/opt ::track) string?
   (std/opt ::album) string?
   (std/opt ::total-time) string?
   (std/opt ::bpm) string?
   (std/opt ::tempos) [ut/tempo-spec]
   (std/opt ::markers) [um/marker-spec]})

(defn distinct-by
  [keyfn coll]
  (map first (vals (group-by keyfn coll))))

(defn sorted-markers
  [item]
  (if (::markers item)
    (update item ::markers #(vec (sort-by ::um/start %)))
    item))

(defn distinct-markers
  [item]
  (if (::markers item)
    (update item ::markers #(vec (distinct-by ::um/num %)))
    item))

(defn tempos->grid-markers
  [{:keys [::tempos] :as item}]
  (reduce #(update %1 ::markers
                   (fn [markers tempo] (conj markers {::um/name ""
                                                      ::um/type ::um/type-grid
                                                      ::um/start (::ut/inizio tempo)
                                                      ::um/end (::ut/inizio tempo)
                                                      ::um/num "-1"})) %2)
          item
          tempos))

(defn remove-grid-markers
  [item]
  (if (::markers item)
    (update item ::markers #(vec (remove (fn [marker] (and
                                                       (or (not (::bpm item)) (= "-1" (::um/num marker)))
                                                       (= ::um/type-grid (::um/type marker)))) %)))
    item))

(defn grid-markers->tempos
  [{:keys [::bpm ::markers] :as item}]
  (as-> item $
    (reduce #(update %1 ::tempos
                     (fn [tempos marker] (if (and
                                              (not= "-1" (::um/num marker))
                                              bpm
                                              (= ::um/type-grid (::um/type marker)))
                                           (vec (conj tempos {::ut/inizio (::um/start marker)
                                                         ::ut/bpm bpm
                                                         ::ut/metro "4/4"
                                                         ::ut/battito "1"}))
                                           tempos)) %2)
            $
            markers)
    (map/remove-nil $ ::tempos)))

(defn sorted-tempos
  [item]
  (if (::tempos item)
    (update item ::tempos #(vec (sort-by ::ut/inizio %)))
    item))

(defn bpm-from-tempos
  [{:keys [::tempos] :as item}]
  (if (not-empty tempos)
    (assoc item ::bpm (::ut/bpm (first tempos))) ; TODO could take average, if bpm's were numeric
    item))

(defn assert-tempo-and-grid-marker-counts
  [{:keys [::tempos ::markers] :as item}]
  (assert (= (count tempos)
             (count (remove #(not= ::um/type-grid (::um/type %)) markers))))
  item)

(def item-spec
  (as->
   (std/spec
    {:name ::item
     :spec item})
   $
    (assoc $ :gen (fn [] (gen/fmap #((comp
                                      assert-tempo-and-grid-marker-counts
                                      bpm-from-tempos
                                      sorted-tempos
                                      grid-markers->tempos
                                      sorted-markers
                                      tempos->grid-markers
                                      distinct-markers
                                      remove-grid-markers) %) (s/gen $))))
    (spec/remove-empty-spec $ ::tempos ::markers)))

(def library
  ; we want a lazy seq for the collection (it can be large) 
  ; but spec-tools data specs doesn't support cat/seq yet
  {::collection (s/cat :items (s/* item-spec))})

(def library-spec
  (std/spec
   {:name ::library
    :spec library}))