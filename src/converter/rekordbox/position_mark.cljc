(ns converter.rekordbox.position-mark
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.map :as map]
   [converter.spec :as spec]
   [spec-tools.data-spec :as std]))

(def position-mark-xml-spec
  (std/spec
   {:name ::position-mark-xml
    :spec {:tag (s/spec #{:POSITION_MARK})
           :attrs {:Name string?
                   :Type string?
                   :Start (s/double-in :min 0 :max 3600 :NaN? false :infinite? false)
                   :End (s/double-in :min 0 :max 7200 :NaN? false :infinite? false)
                   :Num string?
                   :Red int?
                   :Green int?
                   :Blue int?}}}))

(def type-kw->type-num {::type-cue "0"
                        ::type-loop "4"})

(s/def ::type-kw
  (s/spec (set (keys type-kw->type-num))))

(def position-mark-spec
  (std/spec
   {:name ::position-mark
    :spec {::name string?
           ::type ::type-kw
           ::start (s/double-in :min 0 :max 3600 :NaN? false :infinite? false)
           ::end (s/double-in :min 0 :max 7200 :NaN? false :infinite? false) 
           ::num string?
           ::red int?
           ::green int?
           ::blue int?}}))

(defn type->xml
  [{:keys [::type]} position-mark-xml _]
  (assoc position-mark-xml :Type (type-kw->type-num type)))

(defn position-mark->xml
  [position-mark]
  {:tag :POSITION_MARK
   :attrs (map/transform position-mark
                         (partial map/transform-key csk/->PascalCaseKeyword)
                         {::type type->xml})})

;    (map/transform-keys (dissoc position-mark ::type) csk/->PascalCaseKeyword)