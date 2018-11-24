(ns converter.cli
  (:require
   #?(:cljs [cljs.nodejs :as nodejs])
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [slurp spit]])
   #?(:cljs [converter.xmldom])
   [clojure.data.xml :as xml]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [converter.app :as app]
   [converter.error :as err]
   [converter.xml])
  #?(:clj (:gen-class)))

(def cli-options
  [["-c" "--check-input" "Perform checks on the input file " :default false]
   ["-h" "--help"]])

(defn usage
  [options-summary]
  (->> ["Usage: dj-data-converter [options] <input-file>"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn error
  [errors]
  (as-> ["The following errors occurred while parsing the command:"
         ""] $
    (concat $ errors)
    (str/join \newline $)))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (usage summary) :ok? true}
      errors {:exit-message (error errors)}
      (= 1 (count arguments)) {:arguments {:input-file (first arguments) :output-file "rekordbox.xml"} :options options}
      :else
      {:exit-message (usage summary)})))

(defn exit [status message]
  (println message)
  (if (not= 0 status)
    #?(:clj (System/exit status)
       :cljs (.exit nodejs/process status))))

#?(:clj
   (defn process
     [arguments options]
     (try
       (with-open [reader (io/reader (:input-file arguments))
                   writer (io/writer (:output-file arguments))]
         (as-> reader $
           (if (:check options) (xml/parse $ :skip-whitespace true) (xml/parse $))
           (app/convert $ options)
           (xml/emit $ writer)))
       [0 "Conversion completed"]
       (catch Throwable t (do 
                            (err/write-report (err/create-report arguments options (Throwable->map t)))
                            [2 "Problems converting, please provide error-report.edn file..."])))))

#?(:cljs
   (defn process
     [arguments options]
     (try
       (as-> (:input-file arguments) $
         (io/slurp $)
         (xml/parse-str $)
         (if (:check options) (converter.xml/strip-whitespace $) (identity $))
         (app/convert $ options)
         (xml/emit-str $)
         (io/spit (:output-file arguments) $))
       [0 "Conversion completed"]
       (catch :default e (do 
                           (err/write-report (err/create-report arguments options (err/Error->map e)))
                           [2 "Problems converting, please provide error-report.edn file..."])))))

(defn -main
  [& args]
  (let [{:keys [arguments options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (apply exit (process arguments options)))))

#?(:cljs (set! *main-cli-fn* -main))
