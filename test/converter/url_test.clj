(ns converter.url-test
  (:require
   [converter.test-utils :refer [deftest-check]]
   [converter.url :as url]))

(deftest-check test-drive->wsl `url/drive->wsl 100)