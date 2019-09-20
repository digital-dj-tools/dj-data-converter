(ns converter.traktor.cue
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [converter.spec :as spec]
   [converter.universal.marker :as um]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]
   [utils.map :as map]))

; TODO rename to marker-type->cue-type
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

(defn start-plus-len-not-greater-than-max
  [{{:keys [:START :LEN]} :attrs :as cue}]
  (if (< 7200000 (+ START LEN))
    (assoc-in cue [:attrs :LEN] 0)
    cue))

(def cue
  (std/spec {:name ::cue
             :spec {:tag (s/spec #{:CUE_V2})
                    :attrs {:NAME string?
                            :TYPE ::type-num
                            :START (s/double-in :min 0 :max 7200000 :NaN? false :infinite? false) ; millis
                            :LEN (s/double-in :min 0 :max 7200000 :NaN? false :infinite? false) ; millis
                            :HOTCUE ::hotcue}}}))

(defn hidden-cue?
  [cue]
  (= "-1" (-> cue :attrs :HOTCUE)))

(def cue-spec
  (as->
   (std/spec
    {:name ::cue
     :spec cue})
   $
    (assoc $ :gen (fn [] (->>
                          (s/gen $)
                          (gen/fmap #(start-plus-len-not-greater-than-max %))))))) ; TODO set len to zero unless loop

(defn cue->marker-type
  [{:keys [:TYPE]} marker _]
  (assoc marker ::um/type (type-num->type-kw TYPE)))

(defn marker-type->cue
  [{:keys [::um/type]} cue _]
  (assoc cue :TYPE (type-kw->type-num type)))

(defn millis->seconds
  [millis]
  (/ millis 1000))

(defn seconds->millis
  [seconds]
  (* seconds 1000))

(defn cue->marker-end
  [{:keys [:START :LEN]} marker _]
  (assoc marker ::um/end (millis->seconds (+ START LEN))))

(defn marker-end->cue
  [{:keys [::um/start ::um/end] :as marker} cue _]
  (assoc cue :LEN (seconds->millis (- end start))))

(s/fdef cue->marker
  :args (s/cat :cue (spec/xml-zip-spec cue-spec))
  :ret um/marker-spec
  :fn (fn equiv-marker? [{{conformed-cue :cue} :args conformed-marker :ret}]
        (let [cue (s/unform cue-spec conformed-cue)
              marker (s/unform um/marker-spec conformed-marker)
              START (-> cue :attrs :START)
              LEN (-> cue :attrs :LEN)]
          (= (millis->seconds (+ START LEN)) (::um/end marker)))))

(defn cue->marker
  [cue-z]
  (-> cue-z
      zip/node
      :attrs
      (dissoc :DISPL_ORDER :REPEATS)
      (map/transform (partial map/transform-key (comp #(keyword (namespace ::um/unused) %) string/lower-case name))
                     {:TYPE cue->marker-type
                      :START #(assoc %2 ::um/start (millis->seconds (%3 %1)))
                      :LEN cue->marker-end
                      :HOTCUE ::um/num})))

(s/fdef marker->cue
  :args (s/cat :marker um/marker-spec)
  :ret cue-spec)

(defn marker->cue
  [marker]
  {:tag :CUE_V2
   :attrs (map/transform marker
                         (partial map/transform-key (comp keyword string/upper-case name))
                         {::um/type marker-type->cue
                          ::um/start #(assoc %2 :START (seconds->millis (%3 %1)))
                          ::um/end marker-end->cue
                          ::um/num :HOTCUE})})

(defn marker->cue-tagged
  [start]
  {:tag :CUE_V2
   :attrs {:NAME "[djdc]"
           :TYPE (type-kw->type-num ::um/type-grid)
           :START (seconds->millis start)
           :LEN 0
           :HOTCUE "-1"}})

(defn cue-tagged?
  [cue]
  (string/starts-with? (-> cue :attrs :NAME) "[djdc]"))