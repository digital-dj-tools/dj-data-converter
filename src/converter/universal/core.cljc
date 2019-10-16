(ns converter.universal.core
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.spec :as spec]
   [converter.time :as time]
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
   (std/opt ::date-added) ::time/date
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
    (update item ::markers #(vec (concat (distinct-by ::um/num (um/visible-markers %))
                                         (um/hidden-markers %))))
    item))

(defn tempos->grid-markers
  "Returns an item with a hidden grid marker created for each tempo."
  [{:keys [::tempos] :as item}]
  (reduce #(update %1 ::markers
                   (fn [markers tempo] (conj markers {::um/name ""
                                                      ::um/type ::um/type-grid
                                                      ::um/start (::ut/inizio tempo)
                                                      ::um/end (::ut/inizio tempo)
                                                      ::um/num "-1"})) %2)
          item
          tempos))

(defn filter-markers
  [item & marker-types]
  (if (::markers item)
    (assoc item
           ::markers
           (vec (filter #(apply um/marker-of-type? % marker-types) (::markers item))))
    item))

(defn remove-markers
  [item & marker-types]
  (if (::markers item)
    (assoc item
           ::markers
           (vec (remove #(apply um/marker-of-type? % marker-types) (::markers item))))
    item))

(defn marker->tempo
  [bpm marker]
  {::ut/inizio (::um/start marker)
   ::ut/bpm bpm
   ::ut/metro "4/4"
   ::ut/battito "1"})

(defn grid-markers->tempos
  [{:keys [::bpm ::markers] :as item}]
  (let [grid-markers (filter #(um/marker-of-type? % ::um/type-grid) markers)]
    (if (and bpm (not-empty grid-markers))
      (assoc item ::tempos (map (partial marker->tempo bpm) grid-markers))
      item)))

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
    (assoc item ::bpm (::ut/bpm (first tempos)))
    item))

(defn assert-tempo-and-grid-marker-counts
  [{:keys [::tempos ::markers] :as item}]
  (assert (= (count tempos)
             (count (filter #(um/marker-of-type? % ::um/type-grid) markers))))
  item)

(defn item-from-traktor
  [item]
  ((comp
    #(assoc % ::comments "from-traktor")
    assert-tempo-and-grid-marker-counts
    ; TODO map/remove-empty is not working for some reason, tests fail..
    ; #(map/remove-empty % ::markers)
    #(if (empty? (::markers %)) (dissoc % ::markers) %)
    sorted-tempos
    grid-markers->tempos
    #(dissoc % ::tempos)
    sorted-markers
    distinct-markers
    #(if-not (::bpm item) (remove-markers % ::um/type-grid) %))
   item))

(defn item-from-rekordbox
  [item]
  ((comp
    #(assoc % ::comments "from-rekordbox")
    ; TODO map/remove-empty is not working for some reason, tests fail..
    ; #(map/remove-empty % ::markers ::tempos)
    #(if (empty? (::tempos %)) (dissoc % ::tempos) %)
    #(if (empty? (::markers %)) (dissoc % ::markers) %)
    ; TODO add a hidden marker of type cue at first tempo inizio, this is typical
    bpm-from-tempos
    sorted-tempos
    #(if (empty? (::tempos %)) (dissoc % ::bpm) %)
    sorted-markers
    distinct-markers
    #(filter-markers % ::um/type-cue ::um/type-loop))
   item))

(def item-from-traktor-spec
  (spec/with-gen-fmap-spec
    (std/spec {:name ::item :spec item})
    item-from-traktor))

(def item-from-rekordbox-spec
  (spec/with-gen-fmap-spec
    (std/spec {:name ::item :spec item})
    item-from-rekordbox))

(def item-spec
  (st/spec
   (s/or :item-from-traktor item-from-traktor-spec
         :item-from-rekordbox item-from-rekordbox-spec)))

(def library
  ; we want a lazy seq for the collection (it can be large) 
  ; but spec-tools data specs doesn't support cat/seq yet
  {::collection (s/cat :items (s/* item-spec))})

(def library-spec
  (std/spec
   {:name ::library
    :spec library}))

(defn- marker-matching-tempo?
  "Returns true if the marker is a grid marker that matches the tempo (on tempo inizo to marker start)"
  [marker tempo]
  (and (= (::ut/inizio tempo) (::um/start marker))
       (= ::um/type-grid (::um/type marker))))

(defn matching-tempo?
  "Returns true if there is at least one marker matching the tempo."
  [markers tempo]
  (some #(marker-matching-tempo? % tempo) markers))

(defn tempos-without-matching-markers
  "Returns the tempos without a matching marker, as in the tempos whose inizio doesn't have a matching grid marker start"
  [tempos markers]
  ; TODO report warning if tempo bpm differs from item bpm
  (remove (partial matching-tempo? markers) tempos))