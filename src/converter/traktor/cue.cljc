(ns converter.traktor.cue
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.zip :as zip]
   [converter.map :as map]
   [converter.spec :as spec]
   [converter.universal.marker :as um]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]))

(def type-kw->type-num {::um/type-cue "0"
                        ::um/type-fade-in "1"
                        ::um/type-fade-out "2"
                        ::um/type-load "3"
                        ::um/type-grid "4"
                        ::um/type-loop "5"})

(def type-num->type-kw (set/map-invert type-kw->type-num))

(s/def ::type-num
  (s/spec (set (keys type-num->type-kw))))

; what does -1 mean, what is the upper limit
(s/def ::hotcue
  (s/spec #{"-1" "0" "1" "2" "3" "4" "5" "6" "7"}))

(def cue-spec
  (std/spec {:name ::cue
             :spec {:tag (s/spec #{:CUE_V2})
                    :attrs {:NAME string?
                            :TYPE ::type-num
                            :START ::spec/not-neg-double
                            :LEN ::spec/not-neg-double
                            :HOTCUE ::hotcue}}}))

(defn xml->type
  [{:keys [:TYPE]} marker _]
  (assoc marker ::um/type (type-num->type-kw TYPE)))

(defn type->xml
  [{:keys [::um/type]} cue-xml _]
  (assoc cue-xml :TYPE (type-kw->type-num type)))

(defn xml->end
  [{:keys [:START :LEN]} marker _]
  (assoc marker ::um/end (+ START LEN)))

(defn end->xml
  [{:keys [::um/start ::um/end]} cue-xml _]
  (assoc cue-xml :LEN (- end start)))

(s/fdef cue->marker
  :args (s/cat :cue-z (spec/xml-zip-spec cue-spec))
  :ret um/marker-spec)

(defn cue->marker
  [cue-z]
  (-> (:attrs (zip/node cue-z))
      (dissoc :DISPL_ORDER :REPEATS)
      (map/transform (partial map/transform-key (comp #(keyword (namespace ::um/unused) %) str/lower-case name))
                     {:TYPE xml->type
                      :LEN xml->end
                      :HOTCUE ::um/num})))

(s/fdef marker->cue
  :args (s/cat :marker um/marker-spec)
  :ret cue-spec)

(defn marker->cue
  [marker]
  {:tag :CUE_V2
   :attrs (map/transform marker
                         (partial map/transform-key (comp keyword str/upper-case name))
                         {::um/type type->xml
                          ::um/end end->xml
                          ::um/num :HOTCUE})})