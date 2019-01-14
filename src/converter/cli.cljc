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

(def default-arguments
  {:output-file "rekordbox.xml"})

(def option-specs
  [["-h" "--help"]])

(defn usage-message
  [summary]
  (->> ["Usage: dj-data-converter [options] <input-file>"
        ""
        "Options:"
        summary]
       (str/join \newline)))

(defn error-message
  [errors]
  (as-> ["The following errors occurred while parsing the command:"
         ""] $
    (concat $ errors)
    (str/join \newline $)))

(defn parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args option-specs)]
    (cond
      (:help options) {:exit-message (usage-message summary) :help? true}
      errors {:exit-message (error-message errors)}
      (= 1 (count arguments)) {:arguments {:input-file (first arguments)}
                               :options options}
      :else
      {:exit-message (usage-message summary)})))

(defn exit
  [status message]
  (println message)
  (if (not= 0 status)
    #?(:clj (System/exit status)
       :cljs (.exit nodejs/process status))))

#?(:clj
   (defn process
     [config arguments options]
     (try
       (with-open [reader (io/reader (:input-file arguments))
                   writer (io/writer (:output-file arguments))]
         (as-> reader $
           (xml/parse $ :skip-whitespace true)
           (app/convert config $)
           (xml/emit $ writer)))
       [0 "Conversion completed"]
       (catch Throwable t (do
                            (err/write-report (err/create-report arguments options (Throwable->map t)))
                            [2 "Problems converting, please provide error-report.edn file..."])))))

#?(:cljs
   (defn process
     [config arguments options]
     (try
       (as-> (:input-file arguments) $
         (io/slurp $)
         (xml/parse-str $)
         (converter.xml/strip-whitespace $)
         (app/convert config $)
         (xml/emit-str $)
         (io/spit (:output-file arguments) $))
       [0 "Conversion completed"]
       (catch :default e (do
                           (err/write-report (err/create-report arguments options (err/Error->map e)))
                           [2 "Problems converting, please provide error-report.edn file..."])))))

(defn print-progress
  [f]
  (let [item-count (atom 1)]
    (fn [item]
      (when (= 0 (mod @item-count 1000))
        (println ".")
        #?(:clj (flush)))
      (swap! item-count inc)
      (f item))))

(defn process-args
  [config args]
  (let [{:keys [arguments options exit-message help?]} (parse-args args)]
    (if exit-message
      (exit (if help? 0 1) exit-message)
      (apply exit (process config (merge default-arguments arguments) options)))))

(defn -main
  [& args]
  (let [config {:converter app/traktor->rekordbox
                :progress print-progress}]
    (process-args config args)))

#?(:cljs (set! *main-cli-fn* -main))
