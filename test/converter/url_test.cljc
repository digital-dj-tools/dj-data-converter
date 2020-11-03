(ns converter.url-test
  (:require
   [converter.url :as url]
   [plumula.mimolette.alpha :refer [defspec-test]]))

(defspec-test
  drive->wsl
  `url/drive->wsl
  {:opts {:num-tests 100}})
