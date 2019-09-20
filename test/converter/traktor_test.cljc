(ns converter.traktor-test
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test :refer [is] #?@(:cljs [:include-macros true])]
   [clojure.test.check]
   [clojure.test.check.clojure-test :refer [defspec] #?@(:cljs [:include-macros true])]
   [clojure.test.check.properties :as tcp #?@(:cljs [:include-macros true])]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.traktor.cue :as tc]
   [converter.test-utils :as test]
   [converter.universal.core :as u]
   [plumula.mimolette.alpha :refer [defspec-test]]
   [spec-tools.core :as st]))

(defspec-test
  location->url
  `t/location->url
  {:opts {:num-tests 100}})

(defspec-test
  url->location
  `t/url->location
  {:opts {:num-tests 100}})

(defspec-test
  cue->marker
  `tc/cue->marker
  {:opts {:num-tests 100}})

(defspec-test
  marker->cue
  `tc/marker->cue
  {:opts {:num-tests 100}})

(defspec-test
  entry->item
  `t/entry->item
  {:opts {:num-tests 100}})

(defspec-test
  item->entry
  `t/item->entry
  {:opts {:num-tests 100}})

(defspec-test
  library->nml
  `t/library->nml
  {:opts {:num-tests 10}})

(defspec cue-spec-encode-decode-equality
  100
  (tcp/for-all [cue (s/gen tc/cue-spec)]
               (as-> cue $
                 (st/encode tc/cue-spec $ st/string-transformer)
                 (st/decode tc/cue-spec $ st/string-transformer)
                 (is (= cue $)))))

(defspec entry-spec-encode-decode-equality
  10
  (tcp/for-all [entry (s/gen t/entry-spec)]
               (as-> entry $
                 (st/encode t/entry-spec $ st/string-transformer)
                 (spec/decode! t/entry-spec $ st/string-transformer)
                 (is (= entry $)))))

(defspec nml-spec-encode-decode-equality
  10
  (tcp/for-all [nml (s/gen (t/nml-spec))]
               (as-> nml $
                 (st/encode (t/nml-spec) $ st/string-transformer)
                 (spec/decode! (t/nml-spec) $ st/string-transformer)
                 (is (= nml $)))))

(defspec library-spec-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)]
               (as-> library $
                 (test/traktor-round-trip $)
                 (is (= (test/library-equiv-traktor library)
                        (test/library-equiv-traktor $))))))

(defspec library-spec-round-trip-xml-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)]
               (as-> library $
                 (test/traktor-round-trip $)
                 (st/encode (t/nml-spec) $ spec/xml-transformer)
                 (is (= (st/encode (t/nml-spec) library spec/xml-transformer)
                        $)))))
