(ns converter.config
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.time :as time]
   [spec-tools.data-spec :as std]))

(def config-spec
  (std/spec
   {:name ::config
    :spec {:clock ::time/clock}}))