# Inspektor for an iOS app that is not built with Kotlin: Swift, or React Native, whose iOS
# networking is URLSession. One static framework holding the URLSession capture and core,
# remote inspection included.
#
# Not on trunk yet. Build the framework, then point the app's Podfile at this checkout:
#
#   ./gradlew :inspektor-urlsession:assembleInspektorUrlSessionReleaseXCFramework
#
#   pod 'InspektorUrlSession', :path => '../path/to/inspektor',
#       :configurations => ['Debug', 'Release-dev']   # never the build that reaches users
#
# Inspektor is a development tool. Leaving the production configuration out of
# `:configurations` keeps the framework out of that build entirely; app code can test for it
# with `#if canImport(InspektorUrlSession)`.
Pod::Spec.new do |s|
  s.name = 'InspektorUrlSession'
  s.version = File.read(File.join(__dir__, 'gradle.properties'))[/^VERSION_NAME=(.+)$/, 1]
  s.summary = 'HTTP inspection for URLSession, with remote inspection from Eagly.'
  s.homepage = 'https://github.com/ShreyashKore/inspektor'
  s.license = { :type => 'MIT', :file => 'LICENSE' }
  s.author = 'Shreyash Kore'
  s.source = { :git => 'https://github.com/ShreyashKore/inspektor.git', :tag => s.version.to_s }
  s.platform = :ios, '14.0'
  s.vendored_frameworks = 'inspektor-urlsession/build/XCFrameworks/release/InspektorUrlSession.xcframework'
  # SQLDelight's native driver binds to the system SQLite, and a static framework links nothing.
  s.libraries = 'sqlite3'
end
