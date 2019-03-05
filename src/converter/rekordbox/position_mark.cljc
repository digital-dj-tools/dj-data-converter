(ns converter.rekordbox.position-mark
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.map :as map]
   [converter.spec :as spec]
   [converter.universal.marker :as um]
   [spec-tools.data-spec :as std]))

(def position-mark-spec
  (std/spec
   {:name ::position-mark
    :spec {:tag (s/spec #{:POSITION_MARK})
           :attrs {:Name string?
                   :Type string?
                   :Start (s/double-in :min 0 :max 7200 :NaN? false :infinite? false)
                   (std/opt :End) (s/double-in :min 0 :max 7200 :NaN? false :infinite? false)
                   :Num string?
                   :Red int?
                   :Green int?
                   :Blue int?}}}))

(def marker-type->position-mark-type {::um/type-cue "0"
                                      ::um/type-fade-in "0"
                                      ::um/type-fade-out "0"
                                      ::um/type-load "0"
                                      ::um/type-grid "0"
                                      ::um/type-loop "4"})

(def rekordbox-colours {::white [255 255 255]
                        ::green [60 235 80]
                        ::orange [255 140 0]
                        ::yellow [195 175 4]
                        ::pink [222 68 207]})

(def marker-type->rekordbox-colours {::um/type-cue (rekordbox-colours ::green)
                                     ::um/type-fade-in (rekordbox-colours ::pink)
                                     ::um/type-fade-out (rekordbox-colours ::pink)
                                     ::um/type-load (rekordbox-colours ::yellow)
                                     ::um/type-grid (rekordbox-colours ::white)
                                     ::um/type-loop (rekordbox-colours ::orange)})

(defn marker-type->position-mark
  [{:keys [::um/type]} position-mark _]
  (as-> position-mark $
    (assoc $ :Type (marker-type->position-mark-type type))
    (apply assoc $ (reduce-kv #(conj %1 %3 ((marker-type->rekordbox-colours type) %2))
                              []
                              [:Red :Green :Blue]))))

(defn marker-end->position-mark
  [{:keys [::um/type ::um/end]} position-mark _]
  (if (= ::um/type-loop type)
    (assoc position-mark :End end)
    position-mark))

(defn marker->position-mark
  [marker hotcue?]
  {:tag :POSITION_MARK
   :attrs (map/transform marker
                         (partial map/transform-key csk/->PascalCaseKeyword)
                         {::um/type marker-type->position-mark
                          ::um/end marker-end->position-mark
                          ::um/num #(assoc %2 :Num (if hotcue? "-1" (%3 %1)))})})