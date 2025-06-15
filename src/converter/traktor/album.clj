(set! *warn-on-reflection* true)
(ns converter.traktor.album
  (:require [clojure.spec.alpha :as s]))

(s/def ::TRACK string?)

(s/def ::TITLE string?)

(s/def ::track string?)

(s/def ::title string?)