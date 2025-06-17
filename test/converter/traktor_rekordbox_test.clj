(ns converter.traktor-rekordbox-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [is]]
   [clojure.test.check]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as tcp]
   [converter.config :as config]
   [converter.test-utils :as test]
   [converter.universal.core :as u]))

(defspec traktor-rekordbox-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)
                config (s/gen ::config/config)]
               (as-> library $
                 (test/traktor-round-trip config $)
                 (test/rekordbox-round-trip config $)
                 (is (= ((comp test/library-equiv-traktor test/library-equiv-rekordbox) library)
                        ((comp test/library-equiv-traktor test/library-equiv-rekordbox) $))))))

(defspec rekordbox-traktor-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)
                config (s/gen ::config/config)]
               (as-> library $
                 (test/rekordbox-round-trip config $)
                 (test/traktor-round-trip config $)
                 (is (= ((comp test/library-equiv-traktor test/library-equiv-rekordbox) library)
                        ((comp test/library-equiv-traktor test/library-equiv-rekordbox) $))))))