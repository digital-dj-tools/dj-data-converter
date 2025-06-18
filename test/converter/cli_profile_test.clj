(ns converter.cli-profile-test
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is use-fixtures]]
   [converter.app :as app]
   [converter.cli :as cli]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.stats :as stats]
   [converter.test-utils :as test]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [spec-tools.core :as st]
   [taoensso.tufte :refer [p profile]]
   [taoensso.tufte :as tufte]))

; https://stackoverflow.com/questions/31735423/how-to-pass-a-value-from-a-fixture-to-a-test-with-clojure-test

(def ^:dynamic *dir* nil)

(defn with-tmp-dir
  [f]
  (binding [*dir* (test/tmpdir)]
    (.mkdir (io/file *dir*))
    (f)
    (test/rmdir (io/file *dir*))))

(defn with-profiling
  [f]
  (tufte/add-basic-println-handler! {})
  (tufte/set-min-level! 0)
  (f))

(use-fixtures :once with-profiling)

(use-fixtures :each with-tmp-dir)

(defn setup
  [file spec item-spec xml-transformer profile-id n]
  (profile {:id profile-id}
           (p ::setup (with-open [writer (io/writer file)]
                        (as-> (test/library item-spec n) $
                          (do
                            (println "Mean tempo count" (stats/mean-tempos $))
                            (println "Mean marker count" (stats/mean-markers $))
                            $)
                          (st/encode spec $ xml-transformer)
                          (xml/emit $ writer))))))

(deftest ^:profile traktor->rekordbox
  (let [profile-id "traktor->rekordbox with 1000 items"
        input-file (io/file *dir* "cli-profile-traktor.nml")
        _ (setup input-file
                 (t/nml-spec test/config)
                 (spec/such-that-spec u/item-from-traktor-spec
                                      u/item-contains-total-time?
                                      100)
                 t/xml-transformer
                 profile-id
                 1000)
        arguments {:input-file (str input-file)
                   :output-file (str (io/file *dir* "cli-profile-rekordbox.xml"))}
        options {:profile-id profile-id}
        result (cli/process app/basic-edition arguments options)]
    (is (= 0 (first result)))))

(deftest ^:profile rekordbox->traktor
  (let [profile-id "rekordbox->traktor with 1000 items"
        input-file (io/file *dir* "rekordbox.xml")
        _ (setup input-file
                 (r/dj-playlists-spec test/config)
                 (spec/such-that-spec u/item-from-rekordbox-spec
                                      u/item-contains-total-time?
                                      100)
                 r/xml-transformer
                 profile-id
                 1000)
        arguments {:input-file (str input-file)
                   :output-file (str (io/file *dir* "traktor.nml"))}
        options {:profile-id profile-id}
        result (cli/process app/basic-edition arguments options)]
    (is (= 0 (first result)))))