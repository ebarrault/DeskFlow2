// Navigateur sans interface pour la CI (Chrome installé sur les runners GitHub)
module.exports = function (config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    coverageReporter: { dir: require('path').join(__dirname, './coverage'), reporters: [{ type: 'text-summary' }, { type: 'lcov' }] },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    customLaunchers: { ChromeHeadlessCI: { base: 'ChromeHeadless', flags: ['--no-sandbox'] } },
    restartOnFileChange: true,
  });
};
