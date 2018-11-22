(ns converter.rekordbox.tempo
  (:require
   [camel-snake-kebab.core :as csk]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.map :as map]
   [converter.spec :as spec]
   [spec-tools.data-spec :as std]))

(def tempo-xml-spec
  (std/spec
   {:name ::tempo-xml
    :spec {:tag (s/spec #{:TEMPO})
           :attrs {:Inizio ::spec/not-neg-double
                   :Bpm string?
                   :Metro string?
                   :Battito string?}}}))

(def tempo-spec
  (std/spec
   {:name ::tempo
    :spec {::inizio ::spec/not-neg-double
           ::bpm string?
           ::metro string?
           ::battito string?}}))

(defn tempo->xml
  [tempo]
  {:tag :TEMPO
   :attrs (map/transform-keys tempo csk/->PascalCaseKeyword)})