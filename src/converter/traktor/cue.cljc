(ns converter.traktor.cue
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.zip :as zip]
   [converter.map :as map]
   [converter.spec :as spec]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]))

(def type-kw->type-num {::type-cue "0"
                        ::type-fade-in "1"
                        ::type-fade-out "2"
                        ::type-load "3"
                        ::type-grid "4"
                        ::type-loop "5"})

(def type-num->type-kw (set/map-invert type-kw->type-num))

(s/def ::type-num
  (s/spec (set (keys type-num->type-kw))))

(s/def ::type-kw
  (s/spec (set (keys type-kw->type-num))))

; what does -1 mean, what is the upper limit
(s/def ::hotcue
  (s/spec #{"-1" "0" "1" "2" "3" "4" "5" "6" "7"}))

(def cue-xml-spec
  (std/spec {:name ::cue-xml
             :spec {:tag (s/spec #{:CUE_V2})
                    :attrs {:NAME string?
                            :TYPE ::type-num
                            :START ::spec/not-neg-double
                            :LEN ::spec/not-neg-double
                            :HOTCUE ::hotcue}}}))

(def cue-spec
  (std/spec {:name ::cue
             :spec {::name string?
                    ::type ::type-kw
                    ::start ::spec/not-neg-double
                    ::len ::spec/not-neg-double
                    ::hotcue ::hotcue}}))

(defn xml->type
  [{:keys [:TYPE]} cue _]
  (assoc cue ::type (type-num->type-kw TYPE)))

(defn type->xml
  [{:keys [::type]} cue-xml _]
  (assoc cue-xml :TYPE (type-kw->type-num type)))

(s/fdef xml->cue
  :args (s/cat :cue-z (spec/xml-zip-spec cue-xml-spec))
  :ret cue-spec)

(defn xml->cue
  [cue-z]
  (-> (:attrs (zip/node cue-z))
      (dissoc :DISPL_ORDER :REPEATS)
      (map/transform (partial map/transform-key (comp #(keyword (namespace ::unused) %) str/lower-case name))
                     {:TYPE xml->type})))

(s/fdef cue->xml
  :args (s/cat :cue cue-spec)
  :ret cue-xml-spec)

(defn cue->xml
  [cue]
  {:tag :CUE_V2
   :attrs (map/transform cue
                         (partial map/transform-key (comp keyword str/upper-case name))
                         {::type type->xml})})