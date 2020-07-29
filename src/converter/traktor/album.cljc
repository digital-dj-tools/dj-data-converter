(set! *warn-on-reflection* true)
(ns converter.traktor.album
  (:require #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])))

(s/def ::TRACK string?)

(s/def ::TITLE string?)

(s/def ::track string?)

(s/def ::title string?)