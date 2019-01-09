(ns converter.traktor.cue
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
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

(defn len-capped
  [{{:keys [:START :LEN]} :attrs :as cue}]
  ; (or START LEN (println "cue was: " cue))
  (if (< 3600000 (+ START LEN))
    (assoc-in cue [:attrs :LEN] 0)
    cue))

(def cue
  (std/spec {:name ::cue
             :spec {:tag (s/spec #{:CUE_V2})
                    :attrs {:NAME string?
                            :TYPE ::type-num
                            :START (s/double-in :min 0 :max 3600000 :NaN? false :infinite? false) ; millis
                            :LEN (s/double-in :min 0 :max 3600000 :NaN? false :infinite? false) ; millis
                            :HOTCUE ::hotcue}}}))

(def cue-spec
  (as->
   (std/spec
    {:name ::cue
     :spec cue})
   $
    (assoc $ :gen (fn [] (gen/fmap #((comp len-capped) %) (s/gen $))))))

(defn xml->type
  [{:keys [:TYPE]} marker _]
  (assoc marker ::um/type (type-num->type-kw TYPE)))

(defn type->xml
  [{:keys [::um/type]} cue-xml _]
  (assoc cue-xml :TYPE (type-kw->type-num type)))

(defn millis->seconds
  [millis]
  (/ millis 1000))

(defn seconds->millis
  [seconds]
  (* seconds 1000))

(defn xml->end
  [{:keys [:START :LEN]} marker _]
  (assoc marker ::um/end (millis->seconds (+ START LEN))))

(defn end->xml
  [{:keys [::um/start ::um/end] :as marker} cue-xml _]
  (assoc cue-xml :LEN (seconds->millis (- end start))))

(s/fdef cue->marker
  :args (s/cat :cue-z (spec/xml-zip-spec cue-spec))
  :ret um/marker-spec)

(defn cue->marker
  [cue-z]
  (-> (:attrs (zip/node cue-z))
      (dissoc :DISPL_ORDER :REPEATS)
      (map/transform (partial map/transform-key (comp #(keyword (namespace ::um/unused) %) str/lower-case name))
                     {:TYPE xml->type
                      :START #(assoc %2 ::um/start (millis->seconds (%3 %1)))
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
                          ::um/start #(assoc %2 :START (seconds->millis (%3 %1)))
                          ::um/end end->xml
                          ::um/num :HOTCUE})})