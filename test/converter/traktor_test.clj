(ns converter.traktor-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [is]]
   [clojure.test.check]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as tcp]
   [converter.config :as config]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.traktor.cue :as tc]
   [converter.traktor.location :as tl]
   [converter.test-utils :as test :refer [deftest-check]]
   [converter.universal.core :as u]
   [spec-tools.core :as st]))

(deftest-check test-location->url `tl/location->url)

(deftest-check test-url->location `tl/url->location)

(deftest-check test-cue->marker `tc/cue->marker)

(deftest-check test-marker->cue `tc/marker->cue)

(deftest-check test-entry->item `t/entry->item)

(deftest-check test-item->entry `t/item->entry)

(deftest-check test-library->nml `t/library->nml 10)

(defspec cue-spec-encode-decode-equality
  100
  (tcp/for-all [cue (s/gen tc/cue-spec)]
               (as-> cue $
                 (st/encode tc/cue-spec $ t/string-transformer)
                 (st/decode tc/cue-spec $ t/string-transformer)
                 (is (= cue $)))))

(defspec entry-spec-encode-decode-equality
  10
  (tcp/for-all [entry (s/gen t/entry-spec)]
               (as-> entry $
                 (st/encode t/entry-spec $ t/string-transformer)
                 (spec/decode! t/entry-spec $ t/string-transformer)
                 (is (= entry $)))))

(defspec nml-spec-encode-decode-equality
  10
  (tcp/for-all [nml (s/gen (t/nml-spec test/config))]
               (as-> nml $
                 (st/encode (t/nml-spec test/config) $ t/string-transformer)
                 (spec/decode! (t/nml-spec test/config) $ t/string-transformer)
                 (is (= nml $)))))

(defspec library-spec-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)
                config (s/gen ::config/config)]
               (as-> library $
                 (test/traktor-round-trip config $)
                 (is (= (test/library-equiv-traktor library)
                        (test/library-equiv-traktor $))))))

(defspec library-spec-round-trip-xml-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)
                config (s/gen ::config/config)]
               (as-> library $
                 (test/traktor-round-trip config $)
                 (st/encode (t/nml-spec config) $ t/xml-transformer)
                 (is (= (st/encode (t/nml-spec config) library t/xml-transformer)
                        $)))))
