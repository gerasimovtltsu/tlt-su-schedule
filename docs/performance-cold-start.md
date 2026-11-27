# Cold start / TTI — 0.9.2

Static code-review hypotheses; no device profiling or D7 measurements have been collected.

Use signed release APKs (profileable shell enabled), identical device, profile, date, cache and network. Compare 0.9.1 and 0.9.2 with at least 20 cold launches per condition. Force-stop before each launch; do not clear data for cached-start cases. Include cold cached online/offline, empty cache online/offline, student and teacher, dense week, onboarding, notification permission denied. Do not mix first-run and returning-user distributions.

1. Android Studio Profiler > CPU > System Trace: capture process launch to first displayed schedule and first successful calendar interaction. Inspect main, RenderThread, FrameTimeline, disk I/O, binder transactions, thread scheduling. Compare Schedule.render, Schedule.cacheSnapshot, Schedule.parseCache and Schedule.deferredStartup. Cache parsing must not appear on main.
2. CPU sampled call stacks: inspect JSON parsing, LocalDate parsing, View creation, measure/layout, allocation pressure. Check Schedule.fetchVisibleWeek and Schedule.prefetchWeek. Visible data must be published before prefetch finishes.
3. Java/Kotlin allocations in a separate recording: compare repeated renders, TextViews, LinearLayouts, Drawables and JSON objects. Do not compare timing under allocation instrumentation.
4. Network Inspector where supported (HttpURLConnection) or network timestamps plus system trace: verify slow next-week fetch does not block the visible week. Test airplane mode and slow network.
5. adb shell am start -W measures activity launch, not full interactivity. Use TTID and TTFD from Android startup traces; reportFullyDrawn is called after schedule data or terminal error is rendered. Separately measure first usable data and first successful calendar tap; errors must not be treated as successful schedule TTI. Capture p50/p95. Proposed acceptance: improve p50/p95 cached TTI without regressions in empty-cache first load, reminder correctness or profile switching. 

D7: measure day-7 return rate per install cohort with consented analytics if available; app currently has no retention instrumentation. No causal D7 uplift is established by profiling alone. Guardrails: crash/ANR, missed alarms, stale data and refresh errors. No tracking SDK added.
