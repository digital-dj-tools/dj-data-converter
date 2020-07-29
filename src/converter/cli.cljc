(set! *warn-on-reflection* true)
(ns converter.cli
  (:require
   #?(:cljs [cljs.nodejs :as nodejs])
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   #?(:cljs [converter.xmldom])
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.tools.cli :as cli]
   [converter.config :as config]
   [converter.app :as app]
   [converter.error :as err]
   [converter.xml]
   #?(:clj [taoensso.tufte :as tufte :refer (defnp p profile)]
      :cljs [taoensso.tufte :as tufte :refer-macros (defnp p profile)]))
  #?(:clj (:gen-class)))

(def option-specs
  [["-h" "--help"]
   ["-w" "--wsl"]])

(defn usage-message
  [summary]
  (->> ["Usage: dj-data-converter [options] <input-file>"
        ""
        "Options:"
        summary]
       (string/join \newline)))

(defn error-message
  [errors]
  (as-> ["The following errors occurred while parsing the command:"
         ""] $
    (concat $ errors)
    (string/join \newline $)))

(defn parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args option-specs)]
    (cond
      (:help options) {:exit-message (usage-message summary) :help? true}
      errors {:exit-message (error-message errors)}
      (= 1 (count arguments)) {:arguments {:input-file (first arguments)}
                               :options options}
      :else {:exit-message (usage-message summary)})))

; TODO move to utils
(defn println-err
  [& objs]
  #?(:clj (binding [*out* *err*]
            (apply println objs))
     :cljs (binding [*print-fn* *print-err-fn*]
             (apply println objs))))

; TODO move to utils
(defn exit
  [status message]
  (if (= 0 status)
    (println message)
    (println-err message))
  #?(:clj (System/exit status)
     :cljs (.exit nodejs/process status)))

(defn output-dir
  [output-file]
  (when output-file (.getParent (io/file output-file))))

(defn output-file
  ([arguments]
   (output-file arguments nil))
  ([{:keys [output-file] :as arguments} {:keys [output]}]
   (or output-file
       (cond
        ; TODO either throw exception if output is anything else
        ; or guarantee it isn't by spec conform etc
         (= output :traktor) "collection.nml"
         (= output :rekordbox) "rekordbox.xml"))))

#?(:clj
   (defn process
     [edition arguments options]
     (tufte/add-basic-println-handler! {})
     (tufte/set-min-level! (or (:profile-min-level options) 6))
     (try
       (let [config (config/arguments->config arguments)]
         (with-open [reader (io/reader (:input-file arguments))
                     writer (io/writer (output-file arguments config))]
           (profile {}
                    (as-> reader $
                      (p ::parse (xml/parse $ :skip-whitespace true))
                      (p ::convert (app/convert (app/converter edition config) config options $))
                      (p ::emit (xml/emit $ writer))))))
       [0 "Conversion completed"]
       (catch Throwable t (do
                            (-> t
                                Throwable->map
                                (err/create-report arguments options)
                                (err/write-report (output-dir (output-file arguments))))
                            [2 "Problems converting, please provide error-report.edn file..."])))))

#?(:cljs
   (defn process
     [edition arguments options]
     (tufte/add-basic-println-handler! {})
     (tufte/set-min-level! (or (:profile-min-level options) 6))
     (try
       (let [config (config/arguments->config arguments)]
         (profile {}
                  (as-> (:input-file arguments) $
                    (p ::slurp (io/slurp $))
                    (p ::parse (xml/parse-str $))
                    (p ::strip-whitespace (converter.xml/strip-whitespace $))
                    (p ::convert (app/convert (app/converter edition config) config options $))
                    (p ::emit (xml/emit-str $))
                    (p ::spit (io/spit (output-file arguments config) $)))))
       [0 "Conversion completed"]
       (catch :default e (do
                           (-> e
                               err/Error->map
                               (err/create-report arguments options)
                               (err/write-report (output-dir (output-file arguments))))
                           [2 "Problems converting, please provide error-report.edn file..."])))))

(defn -main
  [& args]
  (let [{:keys [options arguments exit-message help?]} (parse-args args)]
    (if exit-message
      (exit (if help? 0 1) exit-message)
      (apply exit (process app/basic-edition arguments options)))))

#?(:cljs (set! *main-cli-fn* -main))
