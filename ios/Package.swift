// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MienMieniOSCore",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "MienMieniOSCore", targets: ["MienMieniOSCore"])
    ],
    targets: [
        .target(name: "MienMieniOSCore")
    ]
)
