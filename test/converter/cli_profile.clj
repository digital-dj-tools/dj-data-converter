(ns converter.cli-profile
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.config :as config]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.stats :as stats]
   [converter.str :as str]
   [converter.test-utils :as test]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [spec-tools.core :as st]))

(defn setup
  [file spec item-spec xml-transformer n]
  (with-open [writer (io/writer file)]
    ; TODO make a library spec that doesn't have a collection
    (as-> (test/library item-spec n) $
      (do 
        (println "Mean tempo count" (stats/mean-tempos $))
        (println "Mean marker count" (stats/mean-markers $))
        $)
      (st/encode spec $ xml-transformer)
      (xml/emit $ writer))))