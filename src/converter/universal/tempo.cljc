(ns converter.universal.tempo
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.spec :as spec]
   [spec-tools.data-spec :as std]))

(def tempo-spec
  (std/spec
   {:name ::tempo
    :spec {::inizio ::spec/not-neg-double ; seconds
           ::bpm string?
           ::metro string?
           ::battito string?}}))