(ns converter.rekordbox.position-mark
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as string]
   [clojure.zip :as zip]
   [converter.spec :as spec]
   [converter.universal.marker :as um]
   [spec-tools.data-spec :as std]
   [utils.map :as map]))

(def marker-type->position-mark-type {::um/type-cue "0"
                                      ::um/type-fade-in "0"
                                      ::um/type-fade-out "0"
                                      ::um/type-load "0"
                                      ::um/type-grid "0"
                                      ::um/type-loop "4"})

(def position-mark-type->marker-type
  {"0" ::um/type-cue
   "4" ::um/type-loop})

(s/def ::position-mark-type
  (s/spec (set (keys position-mark-type->marker-type))))

(defn end-not-before-start
  [{{:keys [:Start :End]} :attrs :as position-mark}]
  (if (and End (< End Start))
    (assoc-in position-mark [:attrs :End] Start)
    position-mark))

(defn type-loop-if-end-otherwise-type-cue
  [{{:keys [:Start :End]} :attrs :as position-mark}]
  (if End
    (assoc-in position-mark [:attrs :Type] "4")
    (assoc-in position-mark [:attrs :Type] "0")))

(defn all-colours-or-none
  [{{:keys [:Red :Green :Blue]} :attrs :as position-mark}]
  (if (not (and Red Green Blue))
    (update-in position-mark [:attrs] dissoc :Red :Green :Blue)
    position-mark))

(def position-mark
  {:tag (s/spec #{:POSITION_MARK})
   :attrs {:Name string?
           :Type ::position-mark-type
           :Start (s/double-in :min 0 :max 7200 :NaN? false :infinite? false)
           (std/opt :End) (s/double-in :min 0 :max 7200 :NaN? false :infinite? false)
           :Num (s/spec #{"-1" "0" "1" "2" "3" "4" "5" "6" "7"})
           (std/opt :Red) (s/int-in 0 256)
           (std/opt :Green) (s/int-in 0 256)
           (std/opt :Blue) (s/int-in 0 256)}})

(defn memory-cue?
  "Returns true if the position mark is a memory cue"
  [position-mark]
  (= "-1" (-> position-mark :attrs :Num)))

(def position-mark-spec
  (as->
   (std/spec
    {:name ::position-mark
     :spec position-mark})
   $
    (assoc $ :gen (fn [] (->> (s/gen $)
                              (gen/fmap (comp end-not-before-start
                                              type-loop-if-end-otherwise-type-cue
                                              all-colours-or-none)))))))

(def rekordbox-colours {::white [255 255 255]
                        ::green [40 226 20]
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
  [memory-cue? {:keys [::um/type]} position-mark _]
  (as-> position-mark $
    (assoc $ :Type (marker-type->position-mark-type type))
    (if (not memory-cue?)
      (apply assoc $ (reduce-kv #(conj %1 %3 ((marker-type->rekordbox-colours type) %2))
                                []
                                [:Red :Green :Blue]))
      $)))

(defn marker-end->position-mark
  [{:keys [::um/type ::um/end]} position-mark _]
  (if (= ::um/type-loop type)
    (assoc position-mark :End end)
    position-mark))

(s/fdef position-mark->marker
  :args (s/cat :position-mark (spec/xml-zip-spec position-mark-spec))
  :ret um/marker-spec
  :fn (fn equiv? [{{conformed-position-mark :position-mark} :args
                   conformed-marker :ret}]
        (let [position-mark (s/unform position-mark-spec conformed-position-mark)
              marker (s/unform um/marker-spec conformed-marker)]
          (and (= (-> position-mark :attrs :Name) (::um/name marker))
               (= (-> position-mark :attrs :Num) (::um/num marker))
               (= (-> position-mark :attrs :Start) (::um/start marker))
               (if (-> position-mark :attrs :End)
                 (= (-> position-mark :attrs :End) (::um/end marker))
                 (= (-> position-mark :attrs :Start) (::um/end marker)))))))

(defn position-mark->marker
  [position-mark-z]
  (-> position-mark-z
      zip/node
      :attrs
      (dissoc :Red :Green :Blue)
      (map/transform (partial map/transform-key (comp #(keyword (namespace ::um/unused) %) string/lower-case name))
                     {:Type #(assoc %2 ::um/type (position-mark-type->marker-type (%1 %3)))})
      (#(merge {::um/end (::um/start %1)} %1))))

(s/fdef marker->position-mark
  :args (s/cat :marker um/marker-spec :memory-cue? boolean?)
  :ret position-mark-spec
  :fn (fn equiv? [{{conformed-marker :marker conformed-memory-cue? :memory-cue?} :args
                   conformed-position-mark :ret}]
        (let [marker (s/unform um/marker-spec conformed-marker)
              memory-cue? (s/unform boolean? conformed-memory-cue?)
              position-mark (s/unform position-mark-spec conformed-position-mark)]
          (if memory-cue?
            (= "-1" (-> position-mark :attrs :Num))
            (and (= (::um/num marker) (-> position-mark :attrs :Num))
                 (= (get (marker-type->rekordbox-colours (::um/type marker)) 0) (-> position-mark :attrs :Red)))))))

(defn marker->position-mark
  ([marker memory-cue?]
   (marker->position-mark marker memory-cue? (::um/name marker)))
  ([marker memory-cue? name]
   {:tag :POSITION_MARK
    :attrs (map/transform marker
                          (partial map/transform-key csk/->PascalCaseKeyword)
                          {::um/name (fn [o n k] (assoc n :Name name)) ; TODO fix this shit
                           ::um/type (partial marker-type->position-mark memory-cue?)
                           ::um/end marker-end->position-mark
                           ::um/num #(assoc %2 :Num (if memory-cue? "-1" (%3 %1)))})}))

(defn marker->position-mark-tagged
  [marker memory-cue?]
  (marker->position-mark marker memory-cue? (string/join " " ["[djdc]" (::um/name marker)])))

(defn position-mark-tagged?
  [position-mark]
  (string/starts-with? (-> position-mark :attrs :Name) "[djdc]"))