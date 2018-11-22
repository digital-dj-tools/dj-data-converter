(ns converter.traktor-test
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test :refer [is] #?@(:cljs [:include-macros true])]
   [clojure.test.check.clojure-test :refer [defspec] #?@(:cljs [:include-macros true])]
   [clojure.test.check.properties :as tcp #?@(:cljs [:include-macros true])]
   [converter.map :as map]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.traktor.cue :as tc]
   [converter.xml :as xml]
   [plumula.mimolette.alpha :refer [defspec-test]]
   [spec-tools.core :as st]))

(defspec-test
  xml->location
  `t/xml->location
  {:opts {:num-tests 100}})

(defspec-test
  xml->cue
  `tc/xml->cue
  {:opts {:num-tests 100}})

(defspec-test
  cue->xml
  `tc/cue->xml
  {:opts {:num-tests 100}})

(defspec-test
  xml->entry
  `t/xml->entry
  {:opts {:num-tests 100}})

(defspec-test
  entry->xml
  `t/entry->xml
  {:opts {:num-tests 100}})

(defspec cue-xml-spec-encode-decode-equality
  100
  (tcp/for-all [cue-xml (s/gen tc/cue-xml-spec)]
               (as-> cue-xml $
                 (st/encode tc/cue-xml-spec $ st/string-transformer)
                 (st/decode tc/cue-xml-spec $ st/string-transformer)
                 (is (= cue-xml $)))))

(defspec entry-xml-spec-encode-decode-equality
  10
  (tcp/for-all [entry-xml (s/gen t/entry-xml-spec)]
               (as-> entry-xml $
                 (st/encode t/entry-xml-spec $ st/string-transformer)
                 (spec/decode! t/entry-xml-spec $ st/string-transformer)
                 (is (= entry-xml $)))))

(defspec nml-xml-spec-encode-decode-equality
  10
  (tcp/for-all [entry-xml (s/gen t/nml-xml-spec)]
               (as-> entry-xml $
                 (st/encode t/nml-xml-spec $ st/string-transformer)
                 (spec/decode! t/nml-xml-spec $ st/string-transformer)
                 (is (= entry-xml $)))))

(defspec nml-spec-round-trip-nml-equality
  10
  (tcp/for-all [nml (s/gen t/nml-spec)]
               (as-> nml $
                 (st/encode t/nml-xml-spec $ spec/xml-transformer)
                 (xml/encode $)
                 (xml/decode $)
                 (spec/decode! t/nml-spec $ spec/xml-transformer)
                 ; for reasons unknown st/decode reverses the order of collections
                ;  (map/reverse $ ::t/cues ::t/collection)
                 (is (= nml $)))))

(defspec nml-spec-round-trip-xml-equality
  10
  (tcp/for-all [nml (s/gen t/nml-spec)]
               (as-> nml $
                 (st/encode t/nml-xml-spec $ spec/xml-transformer)
                 (xml/encode $)
                 (xml/decode $)
                 (spec/decode! t/nml-spec $ spec/xml-transformer)
                 ; for reasons unknown st/decode reverses the order of collections
                ;  (map/reverse $ ::t/cues ::t/collection)
                 (st/encode t/nml-xml-spec $ spec/xml-transformer)
                 (xml/encode $)
                 (is (= (xml/encode (st/encode t/nml-xml-spec nml spec/xml-transformer)) $)))))
