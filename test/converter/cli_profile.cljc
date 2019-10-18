(ns converter.cli-profile
  (:require
   [clojure.data.xml :as xml]
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.config :as config]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.test-utils :as test]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [spec-tools.core :as st]))

#?(:clj
   (defn setup
     [dir file spec item-spec xml-transformer n]
     (.mkdir (io/file dir))
     (with-open [writer (io/writer (io/file dir file))]
       ; TODO make a library spec that doesn't have a collection
       (as-> (test/library item-spec n) $
         (st/encode spec $ xml-transformer)
         (xml/emit $ writer)))))

#?(:cljs
   (defn setup
     [dir file spec item-spec xml-transformer n]
     (.mkdir (io/file dir))
     ; TODO make a library spec that doesn't have a collection
     (as-> (test/library item-spec n) $
       (st/encode spec $ xml-transformer)
       (xml/emit-str $)
       (io/spit (io/file dir file) $))))

(defn teardown
  [dir]
  (test/rmdir (io/file dir)))