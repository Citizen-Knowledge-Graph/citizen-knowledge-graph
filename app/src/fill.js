
let message;

ipcRenderer.on('main-to-site', (event, msg) => {
  console.log("fill.js, main-to-site:", msg);
  message = msg;
  sendQuery(msg.query);
})

function sendQuery(query) {
  fetch('http://localhost:8080/api/v1/ckg/query', {
    method: 'PUT',
    body: query
  })
  .then(response => response.json())
  .then(data => {
    console.log(data);
    let reportDiv = document.getElementById('reportDiv');
    clearDiv(reportDiv);
    let table = document.createElement('table');
    reportDiv.appendChild(table);
    if (data.valuesNotFound.length > 0) {
      buildTableSection(table, "Values not found", data.valuesNotFound.map(val => [val.split('#')[1], '?']));
    }
    if (Object.keys(data.valuesFound).length > 0) {
      buildTableSection(table, "Values found",
          Object.keys(data.valuesFound).map(key => [key.split('#')[1], data.valuesFound[key]]));
      let queryParams = {};
      for (let key of Object.keys(data.valuesFound)) {
        queryParams[key.split('#')[1]] = data.valuesFound[key];
      }
      let btn = buildActionBtn('Fill values on website', () => {
        openInExternalBrowser(message.responseUrl, queryParams);
      });

      appendWarningCheckboxAndLabel(reportDiv, btn);
      reportDiv.appendChild(btn);
    }
  })
  .catch(error => console.error(error))
}
