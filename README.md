# CLDroid

CLDroid provides an end-to-end solution to statically detect cross-layer threats in Android apps (implemented based on Soot framework). Given an Android app, CLDroid first identifies the data pools (e.g., shared preference files and databases) that may be injected by external apps through exported components. Second, CLDroid employs data identifier-based analysis to track the data flow of data items that traverse through the target data pool. Third, CLDroid learns app-specific data use semantics and universally assesses their security risks from the perspectives of two attack vectors (i.e., data loading and data consuming).


