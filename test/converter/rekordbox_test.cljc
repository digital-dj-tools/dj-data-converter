(ns converter.rekordbox-test
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test.check.clojure-test :refer [defspec] #?@(:cljs [:include-macros true])]
   [clojure.test.check.properties :as tcp #?@(:cljs [:include-macros true])]
   [converter.rekordbox.core :as r]
   [converter.xml :as xml]
   [plumula.mimolette.alpha :refer [defspec-test]]
   [spec-tools.core :as st]))

(defspec-test
  track->xml
  `r/track->xml
  {:opts {:num-tests 100}})

