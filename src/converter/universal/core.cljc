(ns converter.universal.core
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.spec :as spec]
   [converter.url :as url]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [utils.map :as map]))

(def item
  {::location ::url/url
   (std/opt ::title) string?
   (std/opt ::artist) string?
   (std/opt ::track-number) string?
   (std/opt ::album) string?
   (std/opt ::total-time) string?
   (std/opt ::bpm) (s/double-in :min 0 :NaN? false :infinite? false)
   (std/opt ::date-added) string? ; TODO use datetime
   (std/opt ::comments) string?
   (std/opt ::genre) string?
   (std/opt ::tempos) [ut/tempo-spec]
   (std/opt ::markers) [um/marker-spec]})

(defn item-contains-total-time?
  "Returns true if the item contains a total time."
  [item]
  (contains? item ::total-time))

(defn distinct-by
  [keyfn coll]
  (map first (vals (group-by keyfn coll))))

(defn sorted-markers
  "Returns an item with markers sorted by start."
  [item]
  (if (::markers item)
    (update item ::markers #(vec (sort-by ::um/start %)))
    item))

(defn distinct-markers
  "Returns an item with markers distinct by num, except for hidden markers."
  [item]
  (if (::markers item)
    (update item ::markers #(vec (concat (distinct-by ::um/num (remove um/hidden-marker? %))
                                         (filter um/hidden-marker? %))))
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
  "Returns an item with all grid markers removed if the item doesn't contain a bpm, 
  otherwise an item with all hidden grid markers removed."
  [item]
  (if (::markers item)
    (update item ::markers #(vec (remove (fn [marker] (and
                                                       (or (not (::bpm item)) (um/hidden-marker? marker))
                                                       (= ::um/type-grid (::um/type marker)))) %)))
    item))

(defn grid-markers->tempos
  "Returns an item with a tempo created for each visible grid marker, if the item contains a bpm."
  [{:keys [::bpm ::markers] :as item}]
  (as-> item $
    (reduce #(update %1 ::tempos
                     (fn [tempos marker] (if (and
                                              (not (um/hidden-marker? marker))
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
  "Returns an item with tempos sorted by inizio."
  [item]
  (if (::tempos item)
    (update item ::tempos #(vec (sort-by ::ut/inizio %)))
    item))

; TODO should probably be the mean tempo, not first tempo
(defn bpm-from-tempos
  "Returns an item with bpm derived from the first tempo."
  [{:keys [::tempos] :as item}]
  (if (not-empty tempos)
    (assoc item ::bpm (::ut/bpm (first tempos))) ; TODO could take average, if bpm's were numeric
    item))

(defn assert-tempo-and-grid-marker-counts
  [{:keys [::tempos ::markers] :as item}]
  (assert (= (count tempos)
             (count (filter #(= ::um/type-grid (::um/type %)) markers))))
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
                                      tempos->grid-markers ; TODO a tempo might not have a corresponding grid marker
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

(defn- marker-matching-tempo?
  [tempo marker] 
  (and (= (::ut/inizio tempo) (::um/start marker))
       (= ::um/type-grid (::um/type marker))))

(defn tempos-without-matching-markers
  "Returns the tempos without a matching marker, as in the tempos whose inizio doesn't have a matching non-grid marker start"
  [tempos markers]
  ; TODO report warning if tempo bpm differs from item bpm
  (filter 
   #(empty? (filter (fn [marker] (marker-matching-tempo? % marker)) markers)) 
   tempos))